/*
 * Copyright (c) 2008-2021, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.samples.jet.files.unifiedapi;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.jet.JetService;
import com.hazelcast.jet.avro.AvroSinks;
import com.hazelcast.samples.jet.files.unifiedapi.generated.AvroTrade;
import com.hazelcast.jet.hadoop.HadoopSinks;
import com.hazelcast.jet.pipeline.BatchSource;
import com.hazelcast.jet.pipeline.BatchStage;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.file.FileFormat;
import com.hazelcast.jet.pipeline.file.FileSources;
import org.apache.avro.Schema;
import org.apache.avro.reflect.ReflectData;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Job;
import org.apache.parquet.avro.AvroParquetOutputFormat;

import java.nio.file.Paths;

/**
 * Converts data from json file to other formats. You don't need to run this.
 * The data is already stored in data/trades.
 */
public class TradeWriter {

    public static void main(String[] args) throws Exception {

        Schema tradeAvroSchema = ReflectData.get().getSchema(AvroTrade.class);

        Job job = Job.getInstance();
        job.setOutputFormatClass(AvroParquetOutputFormat.class);
        AvroParquetOutputFormat.setOutputPath(job, new Path("./data/trades.parquet"));
        AvroParquetOutputFormat.setSchema(job, tradeAvroSchema);

        BatchSource<Trade> source = FileSources.files(Paths.get("data/trades").toAbsolutePath().toString())
                                               .glob("*jsonl")
                                               .format(FileFormat.json(Trade.class))
                                               .build();

        Pipeline p = Pipeline.create();
        BatchStage<Trade> trades = p
                .readFrom(source);

        trades.map(trade -> trade.getTime() + "," + trade.getTicker() + "," + trade.getQuantity() + "," + trade.getPrice())
              .writeTo(Sinks.files("data/trades.csv"));

        trades.map(trade -> new AvroTrade(trade.getTime(), trade.getTicker(), trade.getQuantity(), trade.getPrice()))
              .writeTo(AvroSinks.files("data/trades.avro", AvroTrade.class, tradeAvroSchema));

        trades.map(trade -> new AvroTrade(trade.getTime(), trade.getTicker(), trade.getQuantity(), trade.getPrice()))
              .writeTo(HadoopSinks.outputFormat(job.getConfiguration(), trade -> null, trade -> trade))
              .setLocalParallelism(1);

        HazelcastInstance hz = Hazelcast.bootstrappedInstance();
        JetService jet = hz.getJet();
        com.hazelcast.jet.Job jetJob = jet.newJob(p);

        Thread.sleep(1000);

        jetJob.cancel();
        hz.shutdown();
    }
}
