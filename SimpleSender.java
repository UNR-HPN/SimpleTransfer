import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.ServerSocket;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;
import java.util.NoSuchElementException;
import java.text.DecimalFormat;
import java.io.*;
import java.nio.charset.StandardCharsets;
/**
 * @author ${Bahadir Pehlivan}
 *
 */ 
public class SimpleSender {
	

    static Vector<File> fileVector;

    static boolean allFileTransfersCompleted = false;
    static String fileOrdering = "shuffle";
    static int totalThreads = 0;
    static int maxCC = 20;
    SenderThread[] senderThreads;
    static String model = "ar";
    static int totalFiles = 0;
    static int sentDone = 0;
    static long start = 0;

    static long totalTransferredBytes = 0;

    String host;
    int port;

    public SimpleSender(String h, int p) {
        host = h;
        port = p;
    }

    public void sendAll(int cc) {
        senderThreads = new SenderThread[maxCC];
        for(int i=0; i<cc; i++) {
            Socket s;
            try {
                s = new Socket(host, port);
                s.setSoTimeout(1000000);
                senderThreads[i] = new SenderThread(i, s);
                senderThreads[i].start();
                SimpleSender.totalThreads++;
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
        for(int i=0; i<cc; i++) {
            try {
                senderThreads[i].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }
        }

    }
    public void openNew(int cc){
        System.out.println("[+] New Thread opened");
        int previousTotal = totalThreads;
        for(int i=previousTotal; i<Math.min(cc+previousTotal, maxCC); i++){
            Socket s;
            try {
                s = new Socket(host, port);
                s.setSoTimeout(1000000);
                senderThreads[i] = new SenderThread(i, s);
                senderThreads[i].start();
                SimpleSender.totalThreads++;
            } catch (Exception e) {
                e.printStackTrace();
                return;
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


    public static Vector<File> getFileListVector(String path){	
        File file =new File(path);
        File[] files;
        if(file.isDirectory()) {
            files = file.listFiles();
        } else {
            files = new File[] {file};
        }

        if (fileOrdering.compareTo("shuffle") == 0){
            List<File> fileList = Arrays.asList(files);
            Collections.shuffle(fileList);
            files = fileList.toArray(new File[fileList.size()]);
        } else if (fileOrdering.compareTo("sort") == 0) {
            Arrays.sort(files, new Comparator<File>() {
                    public int compare(File f1, File f2) {
                    try {
                    int i1 = Integer.parseInt(f1.getName());
                    int i2 = Integer.parseInt(f2.getName());
                    return i1 - i2;
                    } catch(NumberFormatException e) {
                    throw new AssertionError(e);
                    }
                    }
                    });
        } else {
            System.out.println("Undefined file ordering:" + fileOrdering);
            System.exit(-1);
        }
        totalFiles = files.length;
        return new Vector<File>(Arrays.asList(files));
    }


    public static void main(String[] args) {
        String destIp = args[0];
        String path = args[1];
        int cc = Integer.valueOf(args[2]);
        double log_freq_in_s = Double.valueOf(args[3]);

        SimpleSender simpleSender = new SimpleSender(destIp, 50515);
        if(args.length > 4) {
            model = args[4];
        }
        if (args.length > 5) {
            fileOrdering = args[5];
        }

        fileVector = getFileListVector(path);
        RunPython runPython = new RunPython(simpleSender);
        MonitorThread monitorThread = new MonitorThread(cc, log_freq_in_s, runPython);

        monitorThread.start();
        runPython.start();
        start = System.currentTimeMillis();

        simpleSender.sendAll(cc);

        long elapsedTime = (System.currentTimeMillis() - start);
        allFileTransfersCompleted = true;
        System.out.println("[+] Transfer is done totalTransferredBytes: "+totalTransferredBytes +" and timeSinceStart: "+((System.currentTimeMillis()-start)/1000.)+" seconds");

        try {
            monitorThread.join();
            runPython.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    static class RunPython extends Thread{
        double[] thptList = new double[30];
        int currentIndex = 0;
        double convergenceThpt = 0.;
        boolean newAdded = false;
        SimpleSender simpleSender = null;
        double[][] runSample = new double[5][1];
        boolean normalTransferStarted = false;
        PrintWriter outPrint = null;
        Socket socket = null;
        ServerSocket serverSocket = null;
        BufferedReader in = null;
        Socket sock = null;

        RunPython(SimpleSender ss){
            simpleSender = ss;
            try {
                sock = new Socket("127.0.0.1", 32015);
                sock.setSoTimeout(1000000);
                System.out.println("[+] Connected to python");

                outPrint = new PrintWriter(new BufferedWriter(new OutputStreamWriter(sock.getOutputStream())), true);
                in = new BufferedReader(new InputStreamReader(sock.getInputStream()));

                System.out.println(model);
            }catch(IOException e){
                e.printStackTrace();
            }
        }
        void sendMessagePython(String message){
            outPrint.println(message);
            System.out.println("[+] Msg sent :"+message);
        }
        double receiveMessagePython(){
            String line;
            try{
                while((line = in.readLine()) != null){
                    double thpt = Double.parseDouble(line.trim());
                    return  thpt;
                }
            }catch(IOException e){
                System.out.println("Exception in reading output"+ e.toString());
            }
            return 0.;
        }
        boolean normalTransfer(){
            return normalTransferStarted;
        }
        void addThpt(double thpt){
            if (currentIndex >= 30){
                return;
            }
            thptList[currentIndex++] = thpt;
            newAdded = true;
        }
        String getThptList(){
            String thpts = model;
            for(int i=0; i<currentIndex;i++){
                thpts = thpts + " " + thptList[i];
            }
            return thpts;
        }
        void startNotSampling(){
            double maxThpt = 0.0;
            int maxConc = 0;
            for(int i=0; i<runSample.length;i++){
                double totalThpt = 0.0;
                for(int j=0; j<runSample[i].length;j++){
                    totalThpt += runSample[i][j];
                }
                totalThpt /= runSample[i].length;
                if(maxThpt < totalThpt){
                    maxThpt = totalThpt;
                    maxConc = i+1;
                }
                System.out.println("[+] Average throughput for cc="+(i+1) + " is "+ totalThpt+" Mbps.");
            }
            System.out.println("[+++++] Max Avg. Throughput is when cc="+maxConc+" and value is "+maxThpt+" Mbps.");
            for(int i=totalThreads-1;i>maxConc-1;i--){
                simpleSender.senderThreads[i].waitHere = true;

            }
            System.out.println("[+] Sampling is done totalTransferredBytes: "+totalTransferredBytes+" and timeSinceStart: "+((System.currentTimeMillis()-start)/1000.)+" seconds");
            normalTransferStarted = true;
        }
        @Override
        public void run() {
            try{
                int i = 1;
                int j = 0;
                long samplingStartTime = System.currentTimeMillis();
                while (!allFileTransfersCompleted) {
                    if(newAdded){
                        newAdded = false;
                        convergenceThpt = runPythonCode(getThptList());
                        if (convergenceThpt > 0.){
                            runSample[i-1][j] = convergenceThpt;
                            System.out.println("[+] Convergence for i="+i+" and j="+j+" is "+convergenceThpt+" Mbps, totalTransferredBytes: "+totalTransferredBytes+" and samplingTime: "+((System.currentTimeMillis()-samplingStartTime)/1000.)+" seconds");
                            samplingStartTime = System.currentTimeMillis();
                            j++;
                            convergenceThpt = 0.;
                            currentIndex = 0;
                            newAdded = false;
                            if(j > 0){
                                i++;
                                simpleSender.openNew(1);
                                newAdded = false;
                                j=0;
                            }
                            if(i > 5){
                                startNotSampling();
                                break;
                            }

                        }
                    }
                    Thread.sleep(100);
                }
                sendMessagePython("done");
                sock.close();
            }catch (InterruptedException e) {
                e.printStackTrace();
            }catch (IOException e) {
                e.printStackTrace();
            }
        }
        public double runPythonCode(String thpts){
            sendMessagePython(thpts);

            return receiveMessagePython();
        }
    }
    public static class MonitorThread extends Thread {
        long lastTransferredBytes = 0;
        int cc = 0;
        double log_freq_in_s = 1.0;
        RunPython runPython = null;

        public MonitorThread(int c, double l, RunPython rp) {
            cc = c;
            log_freq_in_s = l;
            runPython = rp;
        }


        @Override
            public void run() {
                try {
                    double step = 1;
                    while (!allFileTransfersCompleted) {
                        double transferThrInMbps = 8 * (totalTransferredBytes-lastTransferredBytes)/(1000*1000*log_freq_in_s);
                        runPython.addThpt(transferThrInMbps);
                            System.out.println("[logs] "+new DecimalFormat("#0.0000").format(step * log_freq_in_s) + " " + cc + " " + transferThrInMbps);
                        lastTransferredBytes = totalTransferredBytes;
                        Thread.sleep((long) (log_freq_in_s * 1000.0));
                        ++step;
                    }
                    double transferThrInMbps = 8 * (totalTransferredBytes-lastTransferredBytes)/(1000*1000*log_freq_in_s);
                    System.out.println("[logs] "+new DecimalFormat("#0.0000").format(step * log_freq_in_s) + " " + cc + " " + transferThrInMbps);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
    }

    public class SenderThread extends Thread {
        int index = -1;
        private Socket s;
        public boolean waitHere = false;

        public SenderThread(int i, Socket socket){
            index = i;
            s = socket;
        }

        @Override
            public void run() {
                try{
                    File file;
                    DataOutputStream dos = new DataOutputStream(s.getOutputStream());
                    byte[] buffer = new byte[128 * 1024];
                    int n;
                    while (!waitHere || !fileVector.isEmpty()) {
                        try{
                            synchronized(fileVector){
                                file = fileVector.firstElement();
                                fileVector.remove(0);
                            }
                        }catch(NoSuchElementException e){
                            break;
                        }
                        dos.writeUTF(file.getName());
                        dos.writeLong(file.length());
                        FileInputStream fis = new FileInputStream(file);
                        long remaining = file.length();
                        while (remaining > 0) {
                            n = fis.read(buffer);
                            if (n < 0)
                                break;
                            remaining -= n;
                            dos.write(buffer, 0, n);
                            totalTransferredBytes += n;
                        }
                        fis.close();
                        sentDone++;

                    }
                    dos.writeUTF(".");//Terminating Signal
                    dos.close();
                }catch(IOException e){
                    e.printStackTrace(); 
                }   
            }
    }

}
