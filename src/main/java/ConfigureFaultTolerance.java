/*
 * Copyright (c) 2008-2018, Hazelcast, Inc. All Rights Reserved.
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

import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.config.JetConfig;
import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.config.ProcessingGuarantee;

import static java.util.concurrent.TimeUnit.SECONDS;

public class ConfigureFaultTolerance {

    static void s1() {
        //tag::s1[]
        JobConfig jobConfig = new JobConfig();
        jobConfig.setProcessingGuarantee(ProcessingGuarantee.EXACTLY_ONCE);
        jobConfig.setSnapshotIntervalMillis(SECONDS.toMillis(10));
        //end::s1[]

        //tag::s3[]
        jobConfig.setSplitBrainProtection(true);
        //end::s3[]
    }

    static void s2() {
        //tag::s2[]
        JetConfig config = new JetConfig();
        config.getInstanceConfig().setBackupCount(2);
        JetInstance instance = Jet.newJetInstance(config);
        //end::s2[]
    }
}
