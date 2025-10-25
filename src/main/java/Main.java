import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.stream.Stream;
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
               byte[] blobDecoded = inflateZlibByte(blob);
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
               deflateZlibFile(newPath);
           }
       }
       default -> System.out.println("Unknown command: " + command);
     }
  }

  public static byte[] inflateZlibByte(byte[] bytes) {
      Inflater inflater = new Inflater();
      byte[] output = new byte[bytes.length];
      try {
          inflater.setInput(bytes);
          int len = inflater.inflate(output);
          return Arrays.copyOf(output, len);
      } catch (DataFormatException e) {
          throw new RuntimeException(e);
      } finally {
          inflater.end();
      }
  }

  public static void deflateZlibFile(Path path) throws IOException {
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
