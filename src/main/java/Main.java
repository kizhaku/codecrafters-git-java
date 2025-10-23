import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class Main {
  public static void main(String[] args){
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

               try {
                   byte[] blob = Files.readAllBytes(path); //Should be ok for now with small files
                   byte[] blobDecoded = inflateZlibByte(blob);
                   //Find position of null byte
                   int headerEnd = 0;
                   while (headerEnd < blobDecoded.length && blobDecoded[headerEnd] != 0) {
                       headerEnd++;
                   }
                   //File content is of format: blob <blob-size>\0<content>
                   System.out.write(blobDecoded, headerEnd + 1, blobDecoded.length - headerEnd - 1);
               } catch (Exception ex) {
                   throw new RuntimeException(ex);
               }
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
}
