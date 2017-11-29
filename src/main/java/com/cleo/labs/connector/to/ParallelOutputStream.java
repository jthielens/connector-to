package com.cleo.labs.connector.to;

import java.io.IOException;
import java.io.OutputStream;

public class ParallelOutputStream extends OutputStream {
    private OutputStream[] out;

    public ParallelOutputStream(OutputStream[] out) {
        this.out = out;
    }

    @Override
    public void close() throws IOException {
        IOException first = null;
        for (OutputStream os : out) {
            // keep trying to close them all even if one throws an exception
            try {
                os.close();
            } catch (IOException e) {
                if (first == null) {
                    first = e;
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }

    @Override
    public void flush() throws IOException {
        for (OutputStream os : out) {
            os.flush();
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        for (OutputStream os : out) {
            os.write(b);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        for (OutputStream os : out) {
            os.write(b, off, len);
        }
    }

    @Override
    public void write(int b) throws IOException {
        for (OutputStream os : out) {
            os.write(b);
        }
    }
}
