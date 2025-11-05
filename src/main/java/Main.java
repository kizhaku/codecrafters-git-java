import model.GitTree;
import model.TreeEntry;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;

public class Main {
  public static void main(String[] args) throws IOException {
     final String command = args[0];

     switch (command) {
        case "init" -> {
         final File root = new File(".git");
         new File(root, "objects").mkdirs();
         new File(root, "refs").mkdirs();
         final File head = new File(root, "HEAD");

         try {
           head.createNewFile();
           Files.write(head.toPath(), "ref: refs/heads/main\n".getBytes());
           System.out.println("Initialized git directory");
         } catch (IOException e) {
           throw new RuntimeException(e);
         }
        }
       case "cat-file" -> {
           if (args.length >= 3) {
               var blobPath = ".git/objects/%s/%s";
               //Blobs are stored under: objects/<first two chars of hash>/<rest of hash>
               var blobDir = args[2].substring(0, 2);
               var blobName = args[2].substring(2);
               Path path = Path.of(blobPath.formatted(blobDir, blobName));

               byte[] blob = Files.readAllBytes(path); //Should be ok for now with small files
               byte[] blobDecoded = decompressZlibByte(blob);
               //Find position of null byte
               int headerEnd = 0;
               while (headerEnd < blobDecoded.length && blobDecoded[headerEnd] != 0) {
                   headerEnd++;
               }
               //File content is of format: blob <blob-size>\0<content>
               System.out.write(blobDecoded, headerEnd + 1, blobDecoded.length - headerEnd - 1);
           }
       }
       case "hash-object" -> {
           //todo: Defaulting to write -w. Will have to check this
           String prependText = "blob %s\0";
           String basePath = ".git/objects/%s/%s";
           Path newPath;

           if (args.length >= 2) {
               Path file = Path.of(args[2]);
               Path tempFile = Files.createTempFile("prepend", ".tmp");

               try (
                   InputStream in = Files.newInputStream(file);
                   OutputStream out = Files.newOutputStream(tempFile)
               ) {
                   long fileSize = Files.size(file);
                   out.write(prependText.formatted(fileSize).getBytes());
                   in.transferTo(out);

                   String hash = calculateFileHash(tempFile);
                   newPath = Path.of(basePath.formatted(hash.substring(0, 2), hash.substring(2)));
                   //Move temp to original
                   Files.createDirectories(newPath.getParent());
                   System.out.println(hash);
               } catch (Exception ex) {
                   throw new RuntimeException(ex);
               }

               Files.move(tempFile, newPath, StandardCopyOption.REPLACE_EXISTING);
               compressZlibFile(newPath);
           }
       }

       case "ls-tree" -> {
           String basePath = ".git/objects/%s/%s";

           if (args.length >= 2) {
               Path path = Path.of(basePath.formatted(args[2].substring(0, 2), args[2].substring(2)));
               byte[] fileBytesCompressed = Files.readAllBytes(path);
               byte[] fileBytesDecompressed = decompressZlibByte(fileBytesCompressed);

               InputStreamReader inStream = new InputStreamReader(new ByteArrayInputStream(fileBytesDecompressed));
               int in;
               boolean headerFound = false;
               StringBuilder mode = new StringBuilder();
               StringBuilder name = new StringBuilder();
               StringBuilder sha = new StringBuilder();
               int index = 1;
               boolean nameEnd = false;
               boolean modeEnd = false;
               List<TreeEntry> entries = new ArrayList<>();

               while ((in = inStream.read()) != -1) {
                   if (in == 0 && !headerFound) {
                       headerFound = true;
                       index = 0;
                   }

                   if (headerFound) {
                       if (index <= 6 && !modeEnd) {
                           mode.append((char) in);
                       }

                       if (index >= 8 && !nameEnd) {
                           modeEnd = true;

                           if (in == 0) {
                               nameEnd = true;
                               index = 0;
                           } else {
                               name.append((char) in);
                           }
                       }

                       if (nameEnd && index <= 20) {
                           sha.append((char) in);

                           if (index == 20) {
                               nameEnd = false;
                               modeEnd = false;
                               index = 0;

                               entries.add(new TreeEntry(mode.toString(), name.toString(), sha.toString()));
                           }
                       }
                   }
                   
                   index = index + 1;
               }

               GitTree gitTree = new GitTree(entries);

               gitTree.getEntries().forEach(t -> System.out.println(t.getName()));
           }
       }
       default -> System.out.println("Unknown command: " + command);
     }
  }

  public static byte[] decompressZlibByte(byte[] bytes) {
      Inflater inflater = new Inflater();
      byte[] buffer = new byte[bytes.length];

      try {
          inflater.setInput(bytes);
          int len = inflater.inflate(buffer);
          return Arrays.copyOf(buffer, len);
      } catch (DataFormatException e) {
          throw new RuntimeException(e);
      } finally {
          inflater.end();
      }
  }

  public static void compressZlibFile(Path path) throws IOException {
      Path temp = Files.createTempFile("compress-", ".tmp");
      try (
        InputStream in = Files.newInputStream(path);
        OutputStream out = new DeflaterOutputStream(Files.newOutputStream(temp))
      ) {
          in.transferTo(out);
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }

      Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
  }

  private static String calculateFileHash(Path path) {
      try (var in = Files.newInputStream(path)) {
          MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
          in.transferTo(new DigestOutputStream(OutputStream.nullOutputStream(), sha1));
          return HexFormat.of().formatHex(sha1.digest());
      } catch (Exception ex) {
          throw new RuntimeException(ex);
      }
  }
}
