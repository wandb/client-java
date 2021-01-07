package com.wandb.client;

import com.wandb.grpc.InternalServiceGrpc;
import com.wandb.grpc.WandbServer;

import io.grpc.*;
import org.json.JSONObject;

import java.io.*;
import java.util.List;

public class WandbRun {
    final private InternalServiceGrpc.InternalServiceBlockingStub stub;
    final private ManagedChannel channel;
    final private Process grpcProcess;
    final private WandbOutputStream output;
    private WandbServer.RunRecord run = null;
    private int stepCounter;

    private WandbRun(Builder builder) throws IOException, InterruptedException {

        ProcessBuilder pb = new ProcessBuilder("wandb", "grpc-server", "--port", String.valueOf(builder.gprcPort));

        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        this.grpcProcess = pb.start();

        // Connect to GPRC
        this.channel = ManagedChannelBuilder
                .forAddress(builder.gprcAddress, builder.gprcPort)
                .usePlaintext()
                .build();

        this.stub = InternalServiceGrpc.newBlockingStub(this.channel);

        // Initialize with config
        while (this.run == null) {
            try {
                this.run = this.stub.runUpdate(builder.runBuilder.build()).getRun();
            } catch (Exception e) {
                // server was  not yet up, wait a moment and try again
                Thread.sleep(200);
            }
        }
        this.stepCounter = 0;

        // Object for logging stdout to Wandb
        this.output = new WandbOutputStream(this);
        System.setOut(new PrintStream(this.output));

    }

    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("Hello from wandb in java using GRPC!");

        JSONObject config = new JSONObject();
        config.put("data1", 1);
        config.put("data2", "SOME VALUE");

        System.out.println("Creating Runner");
        WandbRun run = new WandbRun.Builder().withConfig(config).build();
        WandbServer.RunRecord data = run.data();

        String baseUrl = "https://app.wandb.ai";

        System.out.println(
                "Monitor your run (" + data.getDisplayName() + ") at: "
                        + baseUrl + "/"
                        + data.getEntity() + "/"
                        + data.getProject() + "/runs/"
                        + data.getRunId()
        );

        for (double i = 0.0; i < 2 * Math.PI; i += 0.05) {
            JSONObject log = new JSONObject();
            log.put("value", Math.sin(i));
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

        run.finish();
    }

    static private WandbServer.HistoryRecord makeLogData(JSONObject json) {
        WandbServer.HistoryRecord.Builder dataBuilder = WandbServer.HistoryRecord.newBuilder();
        for (String key : json.keySet()) {
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

    static private WandbServer.ConfigRecord makeConfigData(JSONObject json) {
        WandbServer.ConfigRecord.Builder dataBuilder = WandbServer.ConfigRecord.newBuilder();
        for (String key : json.keySet()) {
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

    /**
     * Gets the raw data object associated with the run.
     *
     * @return raw data object
     */
    public WandbServer.RunRecord data() {
        return this.run;
    }

    /**
     * Logs data points for the run.
     *
     * @param json data to be logged
     * @return raw log results object
     */
    public WandbServer.HistoryResult log(JSONObject json) {
        return this.log(json, ++this.stepCounter);
    }

    public WandbServer.HistoryResult log(JSONObject json, int step) {
        json.put("_step", step);
        return this.stub.log(makeLogData(json));
    }

    /**
     * Prints run link URL to stdout.
     */
    public void printRunInfo() {
        String baseUrl = this.run.getHost();
        System.out.println("Monitor your run (" + this.run.getDisplayName() + ") at: "
                + baseUrl + "/"
                + this.run.getEntity() + "/"
                + this.run.getProject() + "/runs/"
                + this.run.getRunId());
    }

    public void finish() {
        this.finish(0);
    }

    public void finish(int exitCode) {
        try {
            this.output.flush();
            this.output.resetOut();
            this.output.close();
        } catch (Exception ignore) {
        }

        this.exit(exitCode);
        this.shutdown();
    }

    private WandbServer.RunExitResult exit(int exitCode) {
        return this.stub.runExit(WandbServer.RunExitRecord.newBuilder().setExitCode(exitCode).build());
    }

    private WandbServer.ServerShutdownResult shutdown() {
        WandbServer.ServerShutdownResult result = this.stub.serverShutdown(
                WandbServer.ServerShutdownRequest
                        .newBuilder()
                        .build()
        );

        this.channel.shutdown();
        try {
            this.grpcProcess.waitFor();
        } catch (InterruptedException ignore) {
        }
        return result;
    }

    public static class Builder {
        private WandbServer.RunRecord.Builder runBuilder;
        private int gprcPort = 50051;
        private String gprcAddress = "localhost";

        public Builder() {
            this.runBuilder = WandbServer.RunRecord.newBuilder();
        }

        /**
         * Set a display name for this run, which shows up in the UI and is editable, doesn't have to be unique.
         *
         * @param name display name for the run
         */
        public Builder withName(String name) {
            this.runBuilder.setDisplayName(name);
            return this;
        }

        /**
         * Set a JSON object to set as initial config
         *
         * @param config initial config of the run
         */
        public Builder withConfig(JSONObject config) {
            this.runBuilder.setConfig(makeConfigData(config));
            return this;
        }

        /**
         * Set the name of the project to which this run will belong
         *
         * @param name name of the project this run belongs too
         */
        public Builder withProject(String name) {
            this.runBuilder.setProject(name);
            return this;
        }

        /**
         * Set a string description associated with the run
         *
         * @param notes description associated with the run
         */
        public Builder withNotes(String notes) {
            this.runBuilder.setNotes(notes);
            return this;
        }

        /**
         * Sets the type of job you are logging, e.g. eval, worker, ps (default: training)
         *
         * @param type type of job you are logging
         */
        public Builder setJobType(String type) {
            this.runBuilder.setJobType(type);
            return this;
        }

        /**
         * Set a string by which to group other runs;
         *
         * @param runGroup string for which group this run is apart of
         */
        public Builder withRunGroup(String runGroup) {
            this.runBuilder.setRunGroup(runGroup);
            return this;
        }

        public Builder setSweepId(String sweepId) {
            this.runBuilder.setSweepId(sweepId);
            return this;
        }

        /**
         * Adds a list of strings to associate with this run as tags
         *
         * @param tags list of strings to associate with this run
         */
        public Builder setTags(List<String> tags) {
            this.runBuilder.addAllTags(tags);
            return this;
        }

        /**
         * Removes all tags associated with this run.
         */
        public Builder clearTags() {
            this.runBuilder.clearTags();
            return this;
        }


        public Builder setHost(String host) {
            this.runBuilder.setHost(host);
            return this;
        }

        /**
         * Sets the internal address for the GRPC server
         *
         * @param address GRPC address for this run
         */
        public Builder onAddress(String address) {
            this.gprcAddress = address;
            return this;
        }

        /**
         * Sets the internal port for the GRPC server
         *
         * @param port GRPC port for this run
         */
        public Builder onPort(int port) {
            this.gprcPort = port;
            return this;
        }

        /**
         * Creates a run from the provided configuration
         */
        public WandbRun build() throws IOException, InterruptedException {
            return new WandbRun(this);
        }
    }
}
