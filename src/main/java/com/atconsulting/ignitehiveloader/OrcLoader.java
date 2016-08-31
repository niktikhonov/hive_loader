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

package com.atconsulting.ignitehiveloader;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.ql.io.orc.OrcNewInputFormat;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteDataStreamer;
import org.apache.ignite.Ignition;

/**
 * ORC loader.
 */
public class OrcLoader {
    /**
     * Except the generic arguments the following args are expected: <in_dir> [<in_dir>...] <out_dir>.  
     *
     * @param args The program arguments.
     * @throws Exception On error.
     */
    public static void main(String[] args) throws Exception {
        // Get job parameters.
        String input = System.getProperty(OrcLoaderProperties.PROP_INPUT);

        if (input == null)
            throw new IllegalArgumentException("Input path is not specified " +
                "(set " + OrcLoaderProperties.PROP_INPUT + " property).");

        String output = System.getProperty(OrcLoaderProperties.PROP_OUTPUT);

        if (output == null)
            throw new IllegalArgumentException("Output path is not specified " +
                "(set " + OrcLoaderProperties.PROP_OUTPUT + " property).");

        String cfgPath = System.getProperty(OrcLoaderProperties.PROP_CONFIG_PATH);

        if (cfgPath == null)
            throw new IllegalArgumentException("Path to Ignite XML configuration is not specified " +
                "(set " + OrcLoaderProperties.PROP_CONFIG_PATH + " property).");

        String cacheName = System.getProperty(OrcLoaderProperties.PROP_CACHE_NAME);

        boolean clearCache = Boolean.getBoolean(OrcLoaderProperties.PROP_CLEAR_CACHE);

        int bufSize = Integer.getInteger(OrcLoaderProperties.PROP_BUFFER_SIZE,
            IgniteDataStreamer.DFLT_PER_NODE_BUFFER_SIZE);

        if (bufSize <= 0)
            throw new IllegalArgumentException("Buffer size must be positive: " + bufSize);

        int concurrency = Integer.getInteger(OrcLoaderProperties.PROP_CONCURRENCY, 1);

        if (concurrency <= 0)
            throw new IllegalArgumentException("Concurrency must be positive: " + concurrency);

        boolean filterCurDay = Boolean.getBoolean(OrcLoaderProperties.PROP_FILTER_CURRENT_DAY);

        // Clear cache if needed.
        if (clearCache)
            clearCache(cfgPath, cacheName);

        // Prepare configuration.
        final Configuration conf = new Configuration();

        conf.set(OrcLoaderProperties.PROP_CONFIG_PATH, cfgPath);
        conf.set(OrcLoaderProperties.PROP_CACHE_NAME, cacheName);
        conf.setInt(OrcLoaderProperties.PROP_BUFFER_SIZE, bufSize);
        conf.setInt(OrcLoaderProperties.PROP_CONCURRENCY, concurrency);
        conf.setBoolean(OrcLoaderProperties.PROP_FILTER_CURRENT_DAY, filterCurDay);

        // Prepare job.
        final Job job = Job.getInstance(conf, "Ignite ORC Loader");

        job.setJarByClass(OrcLoader.class);
        job.setInputFormatClass(OrcNewInputFormat.class);
        job.setMapperClass(OrcLoaderMapper.class);
        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(NullWritable.class);

        FileInputFormat.setInputPaths(job, new Path(input));
        FileOutputFormat.setOutputPath(job, new Path(output));

        // Submit the job.
        boolean res = job.waitForCompletion(true);

        System.exit(res ? 0 : 1);
    }

    /**
     * Clear cache using provided configuration.
     *
     * @param cfgPath Path to Ignite XML configuration file.
     * @param cacheName Cache name.
     */
    private static void clearCache(String cfgPath, String cacheName) {
        boolean oldCliMode = Ignition.isClientMode();

        Ignition.setClientMode(true);

        try (Ignite ignite = Ignition.start(cfgPath)) {
            ignite.cache(cacheName).clear();
        }
        finally {
            Ignition.setClientMode(oldCliMode);
        }
    }
}
