package com.wandb.client;

import java.io.OutputStream;
import java.io.PrintStream;

public class WandbOutputStream extends OutputStream {

    private StringBuilder stringBuilder;
    final private WandbRun run;
    final private PrintStream original;

    public WandbOutputStream(WandbRun run) {
        this.run = run;
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

    public void resetOut() {
        System.setOut(this.original);
    }
}
