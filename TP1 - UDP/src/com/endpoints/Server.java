import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by up201304205 on 23-02-2016.
 */
public class Server {
    public static final int USAGE = 0;
    public static final int SOCKETOPENERROR = 1;
    public static final int SOCKETREADERROR = 2;
    public static final int PLATEALREADYEXISTSERROR = 3;
    public static final int DATABASELENGTH = 4;
    public static final int PLATENOTFOUND = 5;
    public static final int PLATEFOUND = 6;
    public static final int COMMANDUNKNOWN = -1;

    public static final int MAX_PACKET_SIZE = 274;
    public static final String NOT_FOUND = "NOT_FOUND";

    private static Map<String, String> plates = new HashMap<>(); /* Plate, owner */

    public static void main(String[] args){
        /* Validate data */
        if(args.length != 2) {
            printLog(USAGE, "");
            return;
        }

        int port = Integer.parseInt(args[1]);
        DatagramSocket socket;

        /* Open socket */
        try {
            socket = new DatagramSocket(port);
        } catch (SocketException e) {
            printLog(SOCKETOPENERROR, args[1]);
            return;
        }

        /* Prepare and wait for messages from clients, parsing them when received. */
        byte[] buf = new byte[MAX_PACKET_SIZE];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);

        while(true){
            try {
                socket.receive(packet);
                int clientPort = packet.getPort();
                InetAddress clientAddress = packet.getAddress();
                byte[] answer = parsePacketInfo(packet);
                socket.send(new DatagramPacket(answer, answer.length, clientAddress, clientPort));
            } catch (IOException e) {
                printLog(SOCKETREADERROR, args[1]);
                return;
            }
        }
    }

    private static byte[] parsePacketInfo(DatagramPacket packet) {
        String message = new String(packet.getData());
        String[] args = message.split(" ");

        if((args[0].equalsIgnoreCase("REGISTER")) && args.length == 3){
            Integer registerResult = registerPlate(args[1], args[2]);
            if(registerResult == -1)
                printLog(PLATEALREADYEXISTSERROR, args[1]);
            else
                printLog(DATABASELENGTH, registerResult.toString());
            message = args[1] + ' ' + args[2];
            return message.getBytes();
        }
        else if(args[0].equalsIgnoreCase("LOOKUP") && args.length == 2){
            String lookupResult = lookupPlate(args[1]);
            if(lookupResult.equals(NOT_FOUND)) {
                printLog(PLATENOTFOUND, args[1]);
                message = "-1\n" + args[1];
            }
            else {
                printLog(PLATEFOUND, lookupResult);
                message = plates.size() + '\n' + args[2] + ' ' + lookupResult;
            }
            return message.getBytes();
        }
        else {
            printLog(COMMANDUNKNOWN, message);
            return new byte[]{(byte)COMMANDUNKNOWN};
        }
    }

    private static String lookupPlate(String plate) {
        if(plates.containsKey(plate))
            return plates.get(plate);
        else return NOT_FOUND;
    }

    private static int registerPlate(String plate, String owner) {
        if(plates.containsKey(plate))
            return -1;
        else{
            plates.put(plate, owner);
            return plates.size();
        }
    }

    private static void printLog(int number, String arg) {
        switch(number){
            case USAGE:
                System.out.println("Usage: java Server <port number>");
                break;
            case SOCKETOPENERROR:
                System.out.println("Error in opening UDP socket in port " + arg + ".");
                break;
            case SOCKETREADERROR:
                System.out.println("Error in reading from UDP socket in port " + arg + ".");
                break;
            case PLATEALREADYEXISTSERROR:
                System.out.println("The plate " + arg + " already exists in the database.");
                break;
            case DATABASELENGTH:
                System.out.println("Plate read. Database length: " + arg + ".");
                break;
            case PLATENOTFOUND:
                System.out.println("Plate " + arg + " was not found in the database.");
                break;
            case PLATEFOUND:
                System.out.println("Plate was found in the database and belongs to " + arg + ".");
                break;
            case COMMANDUNKNOWN:
                System.out.println("Unrecognized command: " + arg);
                break;
            default:
                System.out.println("An unknown error occured.");
                break;
        }
    }
}
