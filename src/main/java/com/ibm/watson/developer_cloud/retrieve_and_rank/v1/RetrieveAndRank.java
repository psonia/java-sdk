/**
 * Copyright 2015 IBM Corp. All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.ibm.watson.developer_cloud.retrieve_and_rank.v1;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonObject;
import com.ibm.watson.developer_cloud.http.HttpHeaders;
import com.ibm.watson.developer_cloud.http.HttpMediaType;
import com.ibm.watson.developer_cloud.http.RequestBuilder;
import com.ibm.watson.developer_cloud.retrieve_and_rank.v1.model.Ranker;
import com.ibm.watson.developer_cloud.retrieve_and_rank.v1.model.Rankers;
import com.ibm.watson.developer_cloud.retrieve_and_rank.v1.model.Ranking;
import com.ibm.watson.developer_cloud.retrieve_and_rank.v1.model.SolrCluster;
import com.ibm.watson.developer_cloud.retrieve_and_rank.v1.model.SolrClusterList;
import com.ibm.watson.developer_cloud.retrieve_and_rank.v1.model.SolrClusterOptions;
import com.ibm.watson.developer_cloud.retrieve_and_rank.v1.model.SolrConfigList;
import com.ibm.watson.developer_cloud.retrieve_and_rank.v1.util.ZipUtils;
import com.ibm.watson.developer_cloud.service.WatsonService;
import com.ibm.watson.developer_cloud.util.GsonSingleton;
import com.ibm.watson.developer_cloud.util.ResponseUtil;
import com.ibm.watson.developer_cloud.util.Validate;
import com.squareup.okhttp.Headers;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.MultipartBuilder;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

/**
 * The IBM Watson Retrieve and Rank service helps users find the most relevant information for their
 * query by using a combination of search and machine learning to find “signals” in the data. Built
 * on top of Apache Solr, developers load their data into the service, train a machine learning
 * model based on known relevant results, then leverage this model to provide improved results to
 * their end users based on their question or query.
 * 
 * @version v1
 * @see <a
 *      href="http://www.ibm.com/smarterplanet/us/en/ibmwatson/developercloud/retrieve-rank.html">
 *      Retrieve and Rank</a>
 */
public class RetrieveAndRank extends WatsonService implements ClusterLifecycleManager,
    SolrConfigManager {

  private static final String ANSWERS = "answers";
  private static final Logger log = Logger.getLogger(RetrieveAndRank.class.getName());
  private static final String NAME = "name";
  /** Path variables */
  private static final String PATH_CREATE_RANKER = "/v1/rankers";

  private static final String PATH_GET_SOLR_CLUSTER = "/v1/solr_clusters/%s";
  private static final String PATH_RANK = "/v1/rankers/%s/rank";
  private static final String PATH_RANKER = "/v1/rankers/%s";
  private static final String PATH_RANKERS = "/v1/rankers";
  private static final String PATH_SOLR_CLUSTERS = "/v1/solr_clusters";
  private static final String PATH_SOLR_CLUSTERS_CONFIG = "/v1/solr_clusters/%s/config";
  private static final String PATH_SOLR_CLUSTERS_CONFIGS = "/v1/solr_clusters/%s/config/%s";
  /** The default URL for the service. */
  private static final String URL = "https://gateway.watsonplatform.net/retrieve-and-rank/api";


  /**
   * Instantiates a new ranker client.
   */
  public RetrieveAndRank() {
    super("retrieve_and_rank");
    setEndPoint(URL);
  }

  /**
   * Creates the Solr configuration path.
   * 
   * @param solrClusterId the solr cluster id
   * @param configName the configuration name
   * @return the string
   */
  private String createConfigPath(String solrClusterId, String configName) {
    Validate.isTrue(solrClusterId != null && !solrClusterId.isEmpty(),
        "solrClusterId cannot be null or empty");
    Validate.isTrue(configName != null && !configName.isEmpty(),
        "configName cannot be null or empty");
    return String.format(PATH_SOLR_CLUSTERS_CONFIGS, solrClusterId, configName);
  }

  /**
   * Sends data to create and train a ranker, and returns information about the new ranker. The
   * status has the value of `Training` when the operation is successful, and might remain at this
   * status for a while.
   * 
   * @param name Name of the ranker
   * @param training The file with the training data i.e., the set of (qid, feature values, and
   *        rank) tuples
   * @return the ranker object
   * @see Ranker
   */
  public Ranker createRanker(final String name, final File training) {
    Validate.notNull(training, "training file cannot be null");
    Validate.isTrue(training.exists(), "training file: " + training.getAbsolutePath()
        + " not found");

    final JsonObject contentJson = new JsonObject();

    if (name != null && !name.isEmpty()) {
      contentJson.addProperty(NAME, name);
    }

    final RequestBody body =
        new MultipartBuilder()
            .type(MultipartBuilder.FORM)
            .addPart(Headers.of("Content-Disposition", "form-data; name=\"training_data\""),
                RequestBody.create(HttpMediaType.BINARY_FILE, training))
            .addPart(Headers.of("Content-Disposition", "form-data; name=\"training_metadata\""),
                RequestBody.create(HttpMediaType.TEXT, contentJson.toString())).build();

    final Request request = RequestBuilder.post(PATH_CREATE_RANKER).withBody(body).build();

    return executeRequest(request, Ranker.class);
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * com.ibm.watson.developer_cloud.retrieve_and_rank.v1.ClusterLifecycleClient#createSolrCluster()
   */
  @Override
  public SolrCluster createSolrCluster() {
    return createSolrCluster(null);
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * com.ibm.watson.developer_cloud.retrieve_and_rank.v1.ClusterLifecycleClient#createSolrCluster
   * (com.ibm.watson.developer_cloud.retrieve_and_rank.v1.models.SolrClusterOptions)
   */
  @Override
  public SolrCluster createSolrCluster(SolrClusterOptions config) {
    final RequestBuilder requestBuilder = RequestBuilder.post(PATH_SOLR_CLUSTERS);

    if (config != null)
      requestBuilder.withBodyContent(GsonSingleton.getGson().toJson(config),
          HttpMediaType.APPLICATION_JSON);

    return executeRequest(requestBuilder.build(), SolrCluster.class);
  }

  /**
   * Deletes a ranker.
   * 
   * @param rankerID the ranker ID
   * @see Ranker
   */
  public void deleteRanker(final String rankerID) {
    Validate.isTrue(rankerID != null && !rankerID.isEmpty(), "rankerId cannot be null or empty");

    final Request request = RequestBuilder.get(String.format(PATH_RANKER, rankerID)).build();
    executeWithoutResponse(request);
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * com.ibm.watson.developer_cloud.retrieve_and_rank.v1.ClusterLifecycleClient#deleteSolrCluster
   * (java.lang.String)
   */
  @Override
  public void deleteSolrCluster(String solrClusterId) {
    Validate.isTrue(solrClusterId != null && !solrClusterId.isEmpty(),
        "solrClusterId cannot be null or empty");
    final Request request =
        RequestBuilder.delete(String.format(PATH_GET_SOLR_CLUSTER, solrClusterId)).build();
    executeWithoutResponse(request);
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.watson.developer_cloud.retrieve_and_rank.v1.SolrConfigManager#
   * deleteSolrClusterConfiguration(java.lang.String, java.lang.String)
   */
  @Override
  public void deleteSolrClusterConfiguration(String solrClusterId, String configName) {
    final String configPath = createConfigPath(solrClusterId, configName);
    final Request request = RequestBuilder.delete(configPath).build();
    executeWithoutResponse(request);
  }

  /**
   * Retrieves the list of rankers for the user.
   * 
   * @return the ranker list
   * @see Ranker
   */
  public Rankers getRankers() {
    final Request request = RequestBuilder.get(PATH_RANKERS).build();

    final Response response = execute(request);
    return ResponseUtil.getObject(response, Rankers.class);
  }

  /**
   * Retrieves the status of a ranker.
   * 
   * @param rankerID the ranker ID
   * @return Ranker object with the status field set
   * @see Ranker
   */
  public Ranker getRankerStatus(final String rankerID) {
    Validate.isTrue(rankerID != null && !rankerID.isEmpty(), "rankerId cannot be null or empty");

    final Request request = RequestBuilder.get(String.format(PATH_RANKER, rankerID)).build();
    return executeRequest(request, Ranker.class);
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.watson.developer_cloud.retrieve_and_rank.v1.ClusterLifecycleClient#getSolrCluster(
   * java.lang.String)
   */
  @Override
  public SolrCluster getSolrCluster(String solrClusterId) {
    Validate.isTrue(solrClusterId != null && !solrClusterId.isEmpty(),
        "solrClusterId cannot be null or empty");
    final Request request =
        RequestBuilder.get(String.format(PATH_GET_SOLR_CLUSTER, solrClusterId)).build();
    return executeRequest(request, SolrCluster.class);

  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * com.ibm.watson.developer_cloud.retrieve_and_rank.v1.SolrConfigManager#getSolrClusterConfiguration
   * (java.lang.String, java.lang.String)
   */
  @Override
  public File getSolrClusterConfiguration(String solrClusterId, String configName) {
    final String configPath = createConfigPath(solrClusterId, configName);
    final RequestBuilder requestBuider = RequestBuilder.get(configPath);
    requestBuider.withHeader(HttpHeaders.ACCEPT, HttpMediaType.APPLICATION_ZIP).build();
    Response response = execute(requestBuider.build());

    try {
      InputStream is = ResponseUtil.getInputStream(response);
      File targetFile = File.createTempFile(configName, ".zip");

      Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
      return targetFile;
    } catch (FileNotFoundException e) {
      log.log(Level.SEVERE, "Temporary file cannot be created", e);
    } catch (IOException e) {
      log.log(Level.SEVERE, "Error writting the Configuration file into a temporary file", e);
    }
    return null;
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * com.ibm.watson.developer_cloud.retrieve_and_rank.v1.SolrConfigManager#listSolrClusterConfigurations
   * (java.lang.String)
   */
  @Override
  public List<String> getSolrClusterConfigurations(String solrClusterId) {
    Validate.isTrue(solrClusterId != null && !solrClusterId.isEmpty(),
        "solrClusterId cannot be null or empty");
    final Request request =
        RequestBuilder.get(String.format(PATH_SOLR_CLUSTERS_CONFIG, solrClusterId)).build();

    SolrConfigList configList = executeRequest(request, SolrConfigList.class);
    return configList.getSolrConfigs();
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * com.ibm.watson.developer_cloud.retrieve_and_rank.v1.ClusterLifecycleClient#listSolrClusters()
   */
  @Override
  public SolrClusterList getSolrClusters() {
    final Request request = RequestBuilder.get(PATH_SOLR_CLUSTERS).build();
    return executeRequest(request, SolrClusterList.class);
  }

  /**
   * Gets and returns the ranked answers.
   * 
   * @param rankerID The ranker ID
   * @param answers The CSV file that contains the search results that you want to rank.
   * @param topAnswers The number of top answers needed, default is 10
   * @return the ranking of the answers
   */
  public Ranking rank(final String rankerID, final File answers, Integer topAnswers) {
    Validate.isTrue(rankerID != null && !rankerID.isEmpty(), "rankerID cannot be null or empty");
    Validate.notNull(answers, "answers file cannot be null");
    Validate.isTrue(answers.exists(), "answers file: " + answers.getAbsolutePath() + " not found");

    final JsonObject contentJson = new JsonObject();
    contentJson.addProperty(ANSWERS, (topAnswers != null && topAnswers > 0) ? topAnswers : 10);

    final RequestBody body =
        new MultipartBuilder()
            .type(MultipartBuilder.FORM)
            .addPart(Headers.of("Content-Disposition", "form-data; name=\"answer_data\""),
                RequestBody.create(HttpMediaType.BINARY_FILE, answers))
            .addPart(Headers.of("Content-Disposition", "form-data; name=\"answer_metadata\""),
                RequestBody.create(HttpMediaType.TEXT, contentJson.toString())).build();

    final String path = String.format(PATH_RANK, rankerID);

    final Request request = RequestBuilder.post(path).withBody(body).build();

    return executeRequest(request, Ranking.class);
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.watson.developer_cloud.retrieve_and_rank.v1.SolrConfigManager#
   * uploadSolrClusterConfigurationDirectory(java.lang.String, java.lang.String, java.io.File)
   */
  @Override
  public void uploadSolrClusterConfigurationDirectory(String solrClusterId, String configName,
      File directory) {
    Validate.notNull(directory, "directory cannot be null");
    Validate
        .isTrue(directory.exists(), "directory : " + directory.getAbsolutePath() + " not found");
    Validate.isTrue(directory.isDirectory(), "directory is not a directory");

    final File zipFile = ZipUtils.createEmptyZipFile(configName);
    try {
      uploadSolrClusterConfigurationZip(solrClusterId, configName, zipFile);
    } finally {
      try {
        Files.delete(zipFile.toPath());
      } catch (final IOException e) {
        zipFile.deleteOnExit();
        log.warning("Error deleting the solr cluster configuration file");
      }
    }

  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.watson.developer_cloud.retrieve_and_rank.v1.SolrConfigManager#
   * uploadSolrClusterConfigurationZip(java.lang.String, java.lang.String, java.io.File)
   */
  @Override
  public void uploadSolrClusterConfigurationZip(String solrClusterId, String configName,
      File zippedConfig) {
    final String configPath = createConfigPath(solrClusterId, configName);
    final RequestBuilder requestBuilder = RequestBuilder.post(configPath);
    requestBuilder.withBody(RequestBody.create(MediaType.parse(HttpMediaType.APPLICATION_ZIP),
        zippedConfig));
    executeWithoutResponse(requestBuilder.build());
  }
}
