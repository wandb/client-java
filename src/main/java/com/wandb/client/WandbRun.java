package com.wandb.client;

import com.wandb.grpc.InternalServiceGrpc;
import com.wandb.grpc.WandbServer;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Timer;

public class WandbRun {
    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("Hello from wandb in java using GRPC!");

        JSONObject config = new JSONObject();
        config.put("data1", 1);
        config.put("data2", "SOME VALUE");

        System.out.println("Creating Runner");
        WandbRun run = new WandbRun.Builder().withConfig(config).build();
        WandbServer.RunData data = run.data();
        String baseUrl = "https://app.wandb.ai";

        System.out.println(
                "Monitor your run (" + data.getDisplayName() + ") at: "
                        + baseUrl + "/"
                        + data.getEntity() + "/"
                        + data.getProject() + "/runs/"
                        + data.getRunId()
        );

        for (double i = 0.0; i < 2 * Math.PI; i += 0.01) {
            JSONObject log = new JSONObject();
            log.put("value", Math.sin(i));
            log.put("value2", Math.sin(i) * 2);
            System.out.println(log);
            run.log(log);
        }

        System.out.println(
                "Finished your run (" + data.getDisplayName() + ") at: "
                        + baseUrl + "/"
                        + data.getEntity() + "/"
                        + data.getProject() + "/runs/"
                        + data.getRunId()
        );

        run.done();
    }

    public static class Builder {
        private WandbServer.RunData.Builder runBuilder;
        private int gprcPort = 50051;
        private String gprcAddress = "localhost";

        public Builder() {
            this.runBuilder = WandbServer.RunData.newBuilder();
        }

        public Builder withName(String name) {
            this.runBuilder.setDisplayName(name);
            return this;
        }

        public Builder withConfig(JSONObject config) {
            this.runBuilder.setConfig(makeConfigData(config));
            return this;
        }

        public Builder withProject(String name) {
            this.runBuilder.setProject(name);
            return this;
        }

        public Builder withNotes(String notes) {
            this.runBuilder.setNotes(notes);
            return this;
        }

        public Builder setJobType(String type) {
            this.runBuilder.setJobType(type);
            return this;
        }

        public Builder withRunGroup(String runGroup) {
            this.runBuilder.setRunGroup(runGroup);
            return this;
        }

        public Builder setSweepId(String sweepId) {
            this.runBuilder.setSweepId(sweepId);
            return this;
        }

        public Builder setHost(String host) {
            this.runBuilder.setHost(host);
            return this;
        }

        public Builder addTag(String tags) {
            this.runBuilder.addTags(tags);
            return this;
        }

        public Builder clearTags() {
            this.runBuilder.clearTags();
            return this;
        }

        public Builder onAddress(String address) {
            this.gprcAddress = address;
            return this;
        }

        public Builder onPort(int port) {
            this.gprcPort = port;
            return this;
        }

        public WandbRun build() throws IOException, InterruptedException {
            return new WandbRun(this);
        }
    }

    private InternalServiceGrpc.InternalServiceBlockingStub stub;
    private ManagedChannel channel;
    private Process grpcProcess;
    private WandbServer.RunData run;
    private int stepCounter;
    private Timer timer;
    private WandbOutputStream output;

    private WandbRun(Builder builder) throws IOException, InterruptedException {

        this.output = new WandbOutputStream();
        System.setOut(new PrintStream(this.output));

        ProcessBuilder pb = new ProcessBuilder("wandb", "grpc-server");
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        this.grpcProcess = pb.start();
        Thread.sleep(1000);

        // Connect to GPRC
        this.channel = ManagedChannelBuilder
                .forAddress(builder.gprcAddress, builder.gprcPort)
                .usePlaintext()
                .build();
        this.stub = InternalServiceGrpc.newBlockingStub(this.channel);

        // Initialize with config
        this.run = this.stub.runUpdate(builder.runBuilder.build()).getRun();
        this.stepCounter = 0;
    }

    public WandbServer.RunData data() {
        return this.run;
    }

    public WandbServer.LogResult log(JSONObject json) {
        return this.log(json, ++this.stepCounter);
    }

    public WandbServer.LogResult log(JSONObject json, int step) {
        json.put("_step", step);
        return this.stub.log(makeLogData(json));
    }

    public void done() {
        this.done(0);
    }

    public void done(int exitCode) {
        try {
            this.output.flush();
            this.output.close();
            Thread.sleep(500);
        }catch (Exception ignore) {}

        this.exit(exitCode);
        this.shutdown();
    }

    private void exit(int exitCode) {
        this.stub.runExit(WandbServer.ExitData.newBuilder().setExitCode(exitCode).build());
    }

    private void shutdown() {
        this.stub.serverShutdown(WandbServer.ServerShutdownRequest.newBuilder().build());
        this.channel.shutdown();
        try {
            this.grpcProcess.waitFor();
        } catch (InterruptedException ignore) {}
    }

    static private WandbServer.HistoryData makeLogData(JSONObject json) {
        WandbServer.HistoryData.Builder dataBuilder = WandbServer.HistoryData.newBuilder();
        for (String key: json.keySet()) {
            Object obj = json.get(key);

            boolean isString = obj instanceof String;
            String jsonValue = isString ? "\"" + obj.toString() + "\"" : obj.toString();

            dataBuilder.addItem(
                    WandbServer.HistoryItem.newBuilder()
                            .setKey(key)
                            .setValueJson(jsonValue)
                            .build()
            );
        }
        return dataBuilder.build();
    }

    static private WandbServer.ConfigData makeConfigData(JSONObject json) {
        WandbServer.ConfigData.Builder dataBuilder = WandbServer.ConfigData.newBuilder();
        for (String key: json.keySet()) {
            Object obj = json.get(key);

            boolean isString = obj instanceof String;
            String jsonValue = isString ? "\"" + obj.toString() + "\"" : obj.toString();

            dataBuilder.addUpdate(
                    WandbServer.ConfigItem.newBuilder()
                            .setKey(key)
                            .setValueJson(jsonValue)
                            .build()
            );
        }
        return dataBuilder.build();
    }
}
