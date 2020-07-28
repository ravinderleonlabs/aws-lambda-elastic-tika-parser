package com.sap.elastic;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.config.RequestConfig;
import org.elasticsearch.ElasticsearchGenerationException;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.json.simple.JSONObject;

import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.sap.aws.AWSRequestSigningApacheInterceptor;

public class AWSElasticIndexer {

	private static String serviceName = "es";
	private static String region = "us-east-1";
	private static String aesEndpoint = "//elastic search url"; //VPC// elastic search URL
	private static String index = "tika-test-index";
	private static String type = "_doc";
	private static String accessKey = "AESDCDyytuytu6565drd";
	private static String secretKey = "WED6AhNJTyrewq43452fff8hgI2Hjf3sAfAalBF";

	static {
		type = System.getenv("type") != null ? System.getenv("type") : "_doc";
		aesEndpoint = System.getenv("aesEndpoint") != null ? System.getenv("aesEndpoint")
				: "search-wdvg-elk-dev-s5ny4pu6d6ab6jhdk2qux4khme.us-east-1.es.amazonaws.com";
		index = System.getenv("index") != null ? System.getenv("index") : "tika-test-index";
	}

	@SuppressWarnings({ "deprecation", "unchecked" })
	public static IndexResponse indexDocument(JSONObject document, LambdaLogger logger) throws Exception {
		logger.log("Indexing document recieved as : " + document);
		RestHighLevelClient esClient = esClient(serviceName, region);
		JSONObject metadata = (JSONObject) document.get("_metadata");
		String id = (metadata.get("FilePath")).toString();
		logger.log("Creating es indesxer client and request with document : " + document);
		IndexResponse response = null;

		try {
			IndexRequest request = new IndexRequest(index, type, id).source(document, XContentType.JSON);
			response = esClient.index(request, RequestOptions.DEFAULT);
		} catch (ElasticsearchGenerationException e) {
			throw new Exception(e);
		}
		logger.log("Inside ES Indexer : Response got is " + response);
		return response;
	}

	public static RestHighLevelClient esClient(String serviceName, String region) {

		BasicAWSCredentials awsCreds = new BasicAWSCredentials(accessKey, secretKey);
		AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(awsCreds);

		AWS4Signer signer = new AWS4Signer();
		signer.setServiceName(serviceName);
		signer.setRegionName(region);
		HttpRequestInterceptor interceptor = new AWSRequestSigningApacheInterceptor(serviceName, signer,
				credentialsProvider);
		return new RestHighLevelClient(RestClient.builder(HttpHost.create(aesEndpoint))
				.setHttpClientConfigCallback(hacb -> hacb.addInterceptorLast(interceptor))
				.setRequestConfigCallback(new RestClientBuilder.RequestConfigCallback() {
					@Override
					public RequestConfig.Builder customizeRequestConfig(RequestConfig.Builder requestConfigBuilder) {
						return requestConfigBuilder.setConnectTimeout(15000).setSocketTimeout(60000);
					}
				}));
	}

	@SuppressWarnings("deprecation")
	public static void main(String[] args) throws IOException {
		RestHighLevelClient esClient = esClient(serviceName, region);

		// Create the document as a hash map
		Map<String, Object> document = new HashMap<>();
		document.put("title", "Walk the Line");
		document.put("director", "James Mangold");
		document.put("year", "2005");

		// Form the indexing request, send it, and print the response
		IndexRequest request = new IndexRequest(index, type, "test-id0-key").source(document);
		IndexResponse response = esClient.index(request, RequestOptions.DEFAULT);
		System.out.println(response.toString());
	}

}
