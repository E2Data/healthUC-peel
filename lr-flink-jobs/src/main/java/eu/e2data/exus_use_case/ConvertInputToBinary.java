/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.e2data.exus_use_case;

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.io.BinaryOutputFormat;
import org.apache.flink.api.common.io.FileOutputFormat;
import org.apache.flink.api.common.io.OutputFormat;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.io.TypeSerializerOutputFormat;
import org.apache.flink.api.java.operators.IterativeDataSet;
import org.apache.flink.api.java.tuple.Tuple1;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.fs.FileSystem;
import org.apache.flink.util.Collector;

import java.io.Serializable;
import java.util.Collection;

/**
 * TODO: add documentation
 */
@SuppressWarnings("serial")
public class ConvertInputToBinary {

	public static void main(String[] args) throws Exception {

		// Checking input parameters
		final ParameterTool params = ParameterTool.fromArgs(args);

		// set up execution environment
		ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
		env.getConfig().setGlobalJobParameters(params); // make parameters available in the web interface

		//default params
		int numberOfFeatures = 82;

		//read train data-set from csv
		DataSet<Tuple1<String>> csv = env.readCsvFile(params.get("input")).fieldDelimiter("\t").ignoreFirstLine()
										 .types(String.class);
		//convert to Data (X, y)
		DataSet<Data> data = csv.flatMap(new ConvertToIndexMatrix(numberOfFeatures));

		// write to binary output format
		FileOutputFormat<Data> of = new TypeSerializerOutputFormat<>();
		data.write(of, params.get("output"), FileSystem.WriteMode.OVERWRITE);

		env.execute();
	}

	static class Data implements Serializable {
		double[] X;
		double y;

		Data(double[] x, double y) {
			X = x;
			this.y = y;
		}
	}

	public static class ConvertToIndexMatrix implements FlatMapFunction<Tuple1<String>, Data> {

		private int n;

		ConvertToIndexMatrix(int n) {
			this.n = n;
		}


		@Override
		public void flatMap(Tuple1<String> stringTuple1, Collector<Data> collector) {
			String line = stringTuple1.f0;
			int col = -1;
			double[] X = new double[n + 1];
			double y = -1.0;
			for (String cell : line.split(",")) {

				//when col is -1 , then take the row number , else collect values
				if (col > -1 && col < n) {
					X[col] = cell != null ? Double.valueOf(cell) : 0.0;
				} else if (col == n) {
					X[col] = 1.0;
					y = Double.valueOf(cell);
				}
				col++;
			}

			collector.collect(new Data(X, y));
		}
	}

}