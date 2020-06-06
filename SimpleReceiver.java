import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
/**
 * @author ${Bahadir Pehlivan}
 *
 */ 
public class SimpleReceiver{

    private ServerSocket ss;
    static String basedir = "/default/path/to/receiver/folder/";


    public SimpleReceiver(int port) {
        try {
            ss = new ServerSocket( port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void listen() {
        while(true) { //Remove this loop to run SimpleReceiver only once
            try {
                Socket clientSock = null;
                clientSock = ss.accept();
                new Thread(new FileSaver(clientSock)).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class FileSaver implements Runnable{
        Socket clientSock;    
        public FileSaver(Socket s){
            clientSock = s;
        }

        public void run() {
            try {
                clientSock.setSoTimeout(100000);
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Connection established from  " + clientSock.getInetAddress());
            try{
                long startTime = System.currentTimeMillis();
                DataInputStream dataInputStream  = new DataInputStream(clientSock.getInputStream());
                byte[] buffer = new byte[128*1024];
                while (true) {
                    String fileName = dataInputStream.readUTF();
                    if (".".equals(fileName)) {
                        //Termination signal
                        break;
                    }
                    long fileSize = dataInputStream.readLong();

                    // System.out.println(fileName + " " + fileSize);
                    OutputStream fos = new FileOutputStream(basedir + "/" +fileName);
                    long remaining = fileSize;
                    long init = System.currentTimeMillis();
                    while(remaining > 0) {
                        int read = dataInputStream.read(buffer, 0, (int)Math.min((long)buffer.length, remaining));
                        remaining -= read;
                        fos.write(buffer, 0, read);
                    }
                    fos.close();
                }
                dataInputStream.close();
                System.out.println("Time " + (System.currentTimeMillis() - startTime) + " ms");
            }catch(IOException e){
                e.printStackTrace();
            }
        }
    }

    public static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    public static void main (String[] args) {
        if (args.length > 0) {
            basedir = args[0];
        }
            SimpleReceiver fs = new SimpleReceiver(50515);
            fs.listen();
    }
}
