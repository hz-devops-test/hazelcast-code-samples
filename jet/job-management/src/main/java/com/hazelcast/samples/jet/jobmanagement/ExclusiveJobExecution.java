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

package com.hazelcast.samples.jet.jobmanagement;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.jet.JetService;
import com.hazelcast.jet.Job;
import com.hazelcast.jet.JobAlreadyExistsException;
import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sources;

import java.util.concurrent.CancellationException;

import static com.hazelcast.internal.util.EmptyStatement.ignore;
import static com.hazelcast.jet.pipeline.JournalInitialPosition.START_FROM_OLDEST;
import static com.hazelcast.jet.pipeline.Sinks.list;

/**
 * We demonstrate how just one running instance of a named job can be created
 * in the cluster with multiple submissions. The first submission call creates
 * the job and the other submission calls will return the same job.
 * This behavior is useful for micro-service deployments where Jet is used
 * in the embedded mode. Users deploy the same self-contained package into
 * multiple instances and each instance submits the same job during startup.
 */
public class ExclusiveJobExecution {

    public static void main(String[] args) {
        // create two instances
        HazelcastInstance hz1 = Hazelcast.newHazelcastInstance();
        HazelcastInstance hz2 = Hazelcast.newHazelcastInstance();

        Pipeline p = Pipeline.create();
        p.readFrom(Sources.<Integer, Integer>mapJournal("source", START_FROM_OLDEST))
                .withoutTimestamps()
                .writeTo(list("sink"));

        JobConfig jobConfig = new JobConfig().setName("namedJob");

        JetService jet1 = hz1.getJet();
        JetService jet2 = hz2.getJet();

        // Submit the same job from multiple Hazelcast instances
        Job job1 = jet1.newJobIfAbsent(p, jobConfig);
        Job job2 = jet2.newJobIfAbsent(p, jobConfig);

        // Both Job handles refer to the same job
        assert job1.getId() == job2.getId();

        try {
            // If I try to submit the same job with newJob(), I will get an exception
            jet1.newJob(p, jobConfig);
            assert false;
        } catch (JobAlreadyExistsException ignored) {
            ignore(ignored);
        }

        job1.cancel();

        try {
            job1.join();
        } catch (CancellationException ignored) {
            ignore(ignored);
        }

        // Now I can submit a new job with the same name because there is
        // no active job with that name
        Job job3 = jet1.newJobIfAbsent(p, jobConfig);

        // This is a new job instance
        assert job3.getId() != job1.getId();

        job3.cancel();
        try {
            job3.join();
        } catch (CancellationException ignored) {
            ignore(ignored);
        }

        hz1.getCluster().shutdown();
    }

}
