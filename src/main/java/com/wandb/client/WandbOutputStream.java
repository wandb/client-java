package com.wandb.client;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class WandbOutputStream extends OutputStream {

    private StringBuilder stringBuilder;
    private PrintStream original;

    public WandbOutputStream() {
        this.original = System.out;
        this.stringBuilder = new StringBuilder();
    }

    @Override
    public void write(int b) {
        int[] bytes = {b};
        write(bytes, 0, bytes.length);
    }

    public void write(int[] bytes, int offset, int length) {
        String s = new String(bytes, offset, length);
        this.stringBuilder.append(s);

        if (this.stringBuilder.length() > 250) {
            this.original.print(this.stringBuilder.toString());
            this.original.println("Flushing output [AUTOMATICALLY]");
            this.stringBuilder = new StringBuilder();
        }
    }

    public void flush() {
        this.original.print(this.stringBuilder.toString());
        this.original.println("Flushing output [FINAL]");
        this.stringBuilder = new StringBuilder();
    }
}
