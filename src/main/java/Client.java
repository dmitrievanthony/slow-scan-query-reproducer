/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class Client {

    private static final int SAMPLES = 20;

    private static final int[] pageSizes = new int[]{5, 10, 20, 50, 100, 150, 200, 300, 400, 500, 600};

    public static void main(String... args) throws IOException {
        for (int pageSize : pageSizes) {
            long[] times = new long[SAMPLES];
            for (int i = 0; i < SAMPLES; i++)
                times[i] = calcScanQueryWaitingTime(pageSize);

            double mean = mean(times);
            double stddev = stddev(times);
            double interval95 = 1.96 * stddev / Math.sqrt(times.length);

            System.out.printf("Page size %d Mb, waiting time %.2f ± %.2f ms\n", pageSize, mean, interval95);
        }
    }

    private static long calcScanQueryWaitingTime(int pageSize) throws IOException {
        Socket sock = new Socket("localhost", 10800);

        ClientOutputStream out = new ClientOutputStream(sock.getOutputStream());
        ClientInputStream in = new ClientInputStream(sock.getInputStream());

        // Handshake.
        out.writeInt(8);                     // Message length.
        out.writeByte(1);                    // Always 1.
        out.writeShort(1);                   // Version major.
        out.writeShort(1);                   // Version minor.
        out.writeShort(0);                   // Version patch.
        out.writeByte(2);                    // Always 2.
        out.flush();

        in.readInt();                          // Handshake result length.
        int res = in.readByte();               // Handshake result.
        if (res != 1)
            throw new IllegalStateException("Handshake failed [res=" + res + "]");

        // Scan Query.
        out.writeInt(25);                    // Message length.
        out.writeShort(2000);                // Scan Query opcode.
        out.writeLong(0);                    // Request ID.
        out.writeInt("TEST_CACHE".hashCode());  // Cache Name hash code.
        out.writeByte(0);                    // Flags.
        out.writeByte(101);                  // Filter (NULL).
        out.writeInt(pageSize);                 // Page Size.
        out.writeInt(-1);                    // Partition to query.
        out.writeByte(1);                    // Local flag.
        out.flush();

        long start = System.currentTimeMillis();

        in.readInt();                          // Result length.

        long end = System.currentTimeMillis();

        sock.close();

        return end - start;
    }

    private static double mean(long[] times) {
        double res = 0;

        for (long time : times)
            res += time;

        return res / times.length;
    }

    private static double stddev(long[] times) {
        double mean = mean(times);

        double res = 0;
        for (long time : times)
            res += Math.pow(time - mean, 2);

        return Math.sqrt(res / times.length);
    }

    private static class ClientOutputStream {

        private final OutputStream out;

        public ClientOutputStream(OutputStream out) {
            this.out = out;
        }

        public void writeByte(int v) throws IOException {
            out.write(v);
        }

        public void writeShort(int v) throws IOException {
            out.write((v) & 0xFF);
            out.write((v >>> 8) & 0xFF);
        }

        public void writeInt(int v) throws IOException {
            out.write((v) & 0xFF);
            out.write((v >>>  8) & 0xFF);
            out.write((v >>> 16) & 0xFF);
            out.write((v >>> 24) & 0xFF);
        }

        public void writeLong(long v) throws IOException {
            out.write((int)((v) & 0xFF));
            out.write((int)((v >>>  8) & 0xFF));
            out.write((int)((v >>> 16) & 0xFF));
            out.write((int)((v >>> 24) & 0xFF));
            out.write((int)((v >>> 32) & 0xFF));
            out.write((int)((v >>> 40) & 0xFF));
            out.write((int)((v >>> 48) & 0xFF));
            out.write((int)((v >>> 56) & 0xFF));
        }

        public void flush() throws IOException {
            out.flush();
        }
    }

    private static class ClientInputStream {

        private final InputStream in;

        public ClientInputStream(InputStream in) {
            this.in = in;
        }

        public byte readByte() throws IOException {
            int ch = in.read();
            if (ch < 0)
                throw new EOFException();
            return (byte)ch;
        }

        public short readShort() throws IOException {
            int ch1 = in.read();
            int ch2 = in.read();
            if ((ch1 | ch2) < 0)
                throw new EOFException();
            return (short)((ch2 << 8) + (ch1));
        }

        public int readInt() throws IOException {
            int ch1 = in.read();
            int ch2 = in.read();
            int ch3 = in.read();
            int ch4 = in.read();
            if ((ch1 | ch2 | ch3 | ch4) < 0)
                throw new EOFException();
            return ((ch4 << 24) + (ch3 << 16) + (ch2 << 8) + (ch1));
        }

        public long readLong() throws IOException {
            int ch1 = in.read();
            int ch2 = in.read();
            int ch3 = in.read();
            int ch4 = in.read();
            int ch5 = in.read();
            int ch6 = in.read();
            int ch7 = in.read();
            int ch8 = in.read();
            if ((ch1 | ch2 | ch3 | ch4) < 0)
                throw new EOFException();
            return (
                ((long)ch8 << 56) +
                ((long)ch7 << 48) +
                ((long)ch6 << 40) +
                ((long)ch5 << 32) +
                (ch4 << 24) +
                (ch3 << 16) +
                (ch2 << 8) +
                (ch1)
            );
        }
    }
}
