package ca.ubc.cs317.dnslookup;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;



public class DNSQueryHandler {

    private static final int DEFAULT_DNS_PORT = 53;
    private static DatagramSocket socket;
    private static boolean verboseTracing = false;

    private static final Random random = new Random();

    private static int questionLength = 0;

    //For testing
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        byte[] hexChars = new byte[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = (byte) HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = (byte) HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars, StandardCharsets.UTF_8);
    }

    /**
     * Sets up the socket and set the timeout to 5 seconds
     *
     * @throws SocketException if the socket could not be opened, or if there was an
     *                         error with the underlying protocol
     */
    public static void openSocket() throws SocketException {
        socket = new DatagramSocket();
        socket.setSoTimeout(5000);
    }

    /**
     * Closes the socket
     */
    public static void closeSocket() {
        socket.close();
    }

    /**
     * Set verboseTracing to tracing
     */
    public static void setVerboseTracing(boolean tracing) {
        verboseTracing = tracing;
    }

    /**
     * Builds the query, sends it to the server, and returns the response.
     *
     * @param message Byte array used to store the query to DNS servers.
     * @param server  The IP address of the server to which the query is being sent.
     * @param node    Host and record type to be used for search.
     * @return A DNSServerResponse Object containing the response buffer and the transaction ID.
     * @throws IOException if an IO Exception occurs
     */
    public static DNSServerResponse buildAndSendQuery(byte[] message, InetAddress server,
                                                      DNSNode node) throws IOException {
        // TODO (PART 1): Implement this
        //Building query header
        int offset = 0;
        byte[] ID = new byte[2];
        random.nextBytes(ID);
        //TODO: Right way of doing this???
        int transactionID = (ID[0] << 8)+ ID[1];

        while (offset < 2) {
            message[offset] = ID[offset];
            offset++;
        }
        while (offset < 12) {
            message[offset] = (byte) ((offset == 5) ? 1 : 0);
            offset++;
        }

        //Building Question
        String[] labels = node.getHostName().split("\\.");
        for (String label : labels) {
            int length = label.length();
            message[offset++] = (byte) length;
            questionLength++;
            for (char c : label.toCharArray()) {
                message[offset++] = (byte )c;
                questionLength++;
            }
        }
        message[offset++] = (byte)0;
        message[offset++] = (byte)0;
        message[offset++] = (byte) node.getType().getCode();
        message[offset++] = (byte)0;
        message[offset] = (byte)1;
        questionLength += 5;
        if (verboseTracing) {
            System.out.println();
            System.out.println();
            String qID = "Query ID    " + transactionID + " " + node.getHostName() + "  " + node.getType() + " --> " +
                    server;
            System.out.println(qID);
        }
        /*
        String msg = bytesToHex(message);
        System.out.println(msg);
        */
        //TODO: make sure you've correctly counted the length!
        //Sending the Query and decoding the response.
        ByteBuffer responseContents;
        DNSServerResponse response;
        byte[] reply = new byte[1024];
        DatagramPacket mp = new DatagramPacket(message, message.length, server, DEFAULT_DNS_PORT);
        DatagramPacket rp = new DatagramPacket(reply, message.length, server, DEFAULT_DNS_PORT);
        try {
            socket.send(mp);
            socket.receive(rp);
        } catch (SocketTimeoutException e1) {
            closeSocket();
            if (verboseTracing) {
                System.out.println();
                System.out.println();
                String qID = "Query ID    " + transactionID + " " + node.getHostName() + "  " + node.getType() + " --> " +
                        server;
                System.out.println(qID);
            }
            openSocket();
            try {
                socket.send(mp);
                socket.receive(rp);
            } catch (SocketTimeoutException e2) {
                return null;
            }
        }
        responseContents = ByteBuffer.wrap(rp.getData());
        return (new DNSServerResponse(responseContents, transactionID));
        //IT WORKS! TODO: ERROR HANDLING, MORE TESTING.
    }

    /**
     * Decodes the DNS server response and caches it.
     *
     * @param transactionID  Transaction ID of the current communication with the DNS server
     * @param responseBuffer DNS server's response
     * @param cache          To store the decoded server's response
     * @return A set of resource records corresponding to the name servers of the response.
     */
    public static Set<ResourceRecord> decodeAndCacheResponse(int transactionID, ByteBuffer responseBuffer,
                                                             DNSCache cache) {
        // TODO (PART 1): Implement this
        byte[] response = responseBuffer.array();
        Set<ResourceRecord> nameServerRRs = new HashSet<ResourceRecord>();
        //Parsing reply for simply the NSs of the response put into return.
        //1. Get AA bit
        int AA = (response[2] >> 2) & 0x01;

        //2. Get Error bits
        int RCODE = response[3] & 0x0F;

        //3. Get RR counts
        int ansCount = (response[6] << 8) + response[7]; //TODO: check this is how you put the bytes together.
        int nsCount = (response[8] << 8) + response[9];
        int otherCount = (response[10] << 8) + response[11];

        //5. Cache literally all RRs returned.
        int RRoffset = questionLength + 11;

        for (int i = 0 ; i < ansCount ; i++) {
            //TODO: I think we do something special
        }

        for (int i = 0 ; i < nsCount ; i++) {
            //TODO: cache and save in set to return
        }

        for (int i = 0 ; i < otherCount ; i++) {
            //TODO: just cache
        }

        //Return is the name servers, regardless of the category they're under!
        return null;
    }

    /**
     * Formats and prints record details (for when trace is on)
     *
     * @param record The record to be printed
     * @param rtype  The type of the record to be printed
     */
    private static void verbosePrintResourceRecord(ResourceRecord record, int rtype) {
        if (verboseTracing)
            System.out.format("       %-30s %-10d %-4s %s\n", record.getHostName(),
                    record.getTTL(),
                    record.getType() == RecordType.OTHER ? rtype : record.getType(),
                    record.getTextResult());
    }
}

