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
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.functions.FunctionAnnotation.ForwardedFields;
import org.apache.flink.api.java.operators.IterativeDataSet;
import org.apache.flink.api.java.tuple.Tuple1;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
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
public class LR {

	public static void main(String[] args) throws Exception {

		// Checking input parameters
		final ParameterTool params = ParameterTool.fromArgs(args);

		// set up execution environment
		ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
		env.getConfig().setGlobalJobParameters(params); // make parameters available in the web interface

		//default params
		int numberOfFeatures = 82;
		double alpha = 0.1;
		double lambda = 0.1;


		//read train data-set from csv
		DataSet<Tuple1<String>> csv = env.readCsvFile(params.get("train")).fieldDelimiter("\t").ignoreFirstLine().types(String.class);
		//convert to Data (X, y)
		DataSet<Data> data = csv.flatMap(new ConvertToIndexMatrix(numberOfFeatures));
		//Initialize W and b
		DataSet<Params> parameters = env.fromElements(new Params(new double[numberOfFeatures + 1], numberOfFeatures));

		// set number of bulk iterations for KMeans algorithm
		IterativeDataSet<Params> loop = parameters.iterate(params.getInt("iterations", 10));

		//train
		DataSet<Params> newParameters = data
				// compute a single step using every sample
				.map(new SubUpdate(numberOfFeatures, alpha)).withBroadcastSet(loop, "parameters")
				// sum up all the steps
				.reduce(new UpdateAccumulator(numberOfFeatures))
				// average the steps and update all parameters
				.map(new Update());

		// feed new parameters back into next iteration
		DataSet<Params> final_params = loop.closeWith(newParameters);

		//read train data-set from csv
		DataSet<Tuple1<String>> test_csv = env.readCsvFile(params.get("test")).fieldDelimiter("\t").ignoreFirstLine().types(String.class);
		//convert to Data (X, y)
		DataSet<Data> test_data = test_csv.flatMap(new ConvertToIndexMatrix(numberOfFeatures));
		//evaluate results
		DataSet<String> results = test_data
				.map(new Predict(numberOfFeatures)).withBroadcastSet(final_params, "params")
				.reduce(new Evaluate())
				.map(new ComputeMetrics());

		// emit result
		if (params.has("output")) {

			results
					.writeAsText(params.get("output"), FileSystem.WriteMode.OVERWRITE);

			// since file sinks are lazy, we trigger the execution explicitly
			env.execute("Exus Use Case");
		} else {
			System.out.println("Printing result to stdout. Use --output to specify output path.");
			results.print();
		}
	}

	// *************************************************************************
	//     DATA SOURCE READING (POINTS AND CENTROIDS)
	// *************************************************************************
	static class Data {
		double[] X;
		double y;

		Data(double[] x, double y) {
			X = x;
			this.y = y;
		}
	}

	/**
	 * A set of parameters -- theta_0, theta_1, ... , theta_n
	 */

	static class Params {
		double[] W;
		private int n;

		Params(double[] w, int n) {
			W = w;
			this.n = n;
		}

		public Params div(Integer a) {
			for (int j = 0; j < n + 1; j++) {
				this.W[j] = W[j] / a;
			}
			return this;
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

	// *************************************************************************
	//     DATA TYPES
	// *************************************************************************

	// *************************************************************************
	//     USER FUNCTIONS
	// *************************************************************************

	/**
	 * Compute a single BGD type update for every parameters.
	 */
	public static class SubUpdate extends RichMapFunction<Data, Tuple2<Params, Integer>> {

		private Collection<Params> parameters;

		private Params parameter;

		private int count = 1;

		private int n;
		private double lr;

		SubUpdate(int n, double lr) {
			this.n = n;
			this.lr = lr;
		}

		/**
		 * Reads the parameters from a broadcast variable into a collection.
		 */
		@Override
		public void open(Configuration parameters) {
			this.parameters = getRuntimeContext().getBroadcastVariable("parameters");
		}

		@Override
		public Tuple2<Params, Integer> map(Data in) {

			// TODO: Could this be moved out of the map function?
			for (Params p : parameters) {
				this.parameter = p;
			}

			double z = 0.0;
			for (int j = 0; j < n + 1; j++) {
				z += in.X[j] * parameter.W[j];
			}

			double error = (double) 1 / (1 + Math.exp(-z)) - in.y;

			for (int j = 0; j < n + 1; j++) {
				in.X[j] = parameter.W[j] - lr * (error * in.X[j]);
			}

			return new Tuple2<>(new Params(in.X, n), count);

		}
	}

	/**
	 * Compute TP,TN,FP,FN
	 */
	public static class Predict extends RichMapFunction<Data, Tuple4<Integer, Integer, Integer, Integer>> {

		private Collection<Params> parameters;

		private Params parameter;

		private int n;

		Predict(int n) {
			this.n = n;
		}

		/**
		 * Reads the parameters from a broadcast variable into a collection.
		 */
		@Override
		public void open(Configuration parameters) {
			this.parameters = getRuntimeContext().getBroadcastVariable("params");
		}

		@Override
		public Tuple4<Integer, Integer, Integer, Integer> map(Data in) {

			// TODO: Could this be moved out of the map function?
			for (Params p : parameters) {
				this.parameter = p;
			}

			double z = 0.0;
			for (int j = 0; j < n + 1; j++) {
				z += in.X[j] * parameter.W[j];
			}

			double predict = ((double) 1 / (1 + Math.exp(-z))) > 0.5 ? 1.0 : 0.0;

			if (predict == 0.0 && in.y == 0.0) {
				return new Tuple4<>(0, 1, 0, 0); // tn
			} else if (predict == 0.0 && in.y == 1.0) {
				return new Tuple4<>(0, 0, 0, 1); // fn
			} else if (predict == 1.0 && in.y == 0.0) {
				return new Tuple4<>(0, 0, 1, 0); // fp
			} else if (predict == 1.0 && in.y == 1.0) {
				return new Tuple4<>(1, 0, 0, 0); // tp
			} else {
				return new Tuple4<>(0, 0, 0, 0); // tp
			}

		}
	}

	/**
	 * Accumulator all the update.
	 */
	public static class UpdateAccumulator implements ReduceFunction<Tuple2<Params, Integer>> {

		private int n;

		UpdateAccumulator(int n) {
			this.n = n;
		}

		@Override
		public Tuple2<Params, Integer> reduce(Tuple2<Params, Integer> val1, Tuple2<Params, Integer> val2) {

			for (int j = 0; j < n + 1; j++) {
				val1.f0.W[j] = val1.f0.W[j] + val2.f0.W[j];
			}
			return new Tuple2<>(val1.f0, val1.f1 + val2.f1);

		}
	}

	public static class Evaluate implements ReduceFunction<Tuple4<Integer, Integer, Integer, Integer>> {

		@Override
		public Tuple4<Integer, Integer, Integer, Integer> reduce(Tuple4<Integer, Integer, Integer, Integer> val1, Tuple4<Integer, Integer, Integer, Integer> val2) {
			return new Tuple4<>(val1.f0 + val2.f0, val1.f1 + val2.f1, val1.f2 + val2.f2, val1.f3 + val2.f3);
		}
	}

	/**
	 * Compute the final update by average them.
	 */
	public static class Update implements MapFunction<Tuple2<Params, Integer>, Params> {

		@Override
		public Params map(Tuple2<Params, Integer> arg0) {
			return arg0.f0.div(arg0.f1);
		}

	}


	public static class ComputeMetrics implements MapFunction<Tuple4<Integer, Integer, Integer, Integer>, String> {
		@Override
		public String map(Tuple4<Integer, Integer, Integer, Integer> v) {
			double acc = 1.0 - (double) (v.f2 + v.f3) / (v.f0 + v.f1 + v.f2 + v.f3);
			double pr = (double) v.f0 / (v.f0 + v.f2);
			double rec = (double) v.f0 / (v.f0 + v.f3);
			double f1 = (2 * pr * rec) / (pr + rec);
			return "ACCURACY: " + acc + "\n" + "PRECISION: " + pr + "\n" + "RECALL: " + rec + "\n" + "f1 MEASURE: " + f1 + "\n";
		}

	}
}