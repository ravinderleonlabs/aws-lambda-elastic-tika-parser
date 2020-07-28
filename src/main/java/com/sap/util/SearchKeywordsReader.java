package com.sap.util;

import java.io.IOException;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.util.IOUtils;

public class SearchKeywordsReader {
	
	public static JSONObject getKeyValueMap(String bucket, LambdaLogger logger)
			throws IOException, ParseException {

		String bucketName = System.getenv("searchKeyValueBucket");
		String fileName = System.getenv("searchKeyValueFileName");
		
		String s3AccessKeyForMDFile = System.getenv("s3_access_key_md_file");
		String s3SecretKeyForMDFile = System.getenv("s3_secret_key_md_file");
		
		BasicAWSCredentials awsCreds = new BasicAWSCredentials(s3AccessKeyForMDFile, s3SecretKeyForMDFile);
		AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
		                        .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
		                        .build();
		
		logger.log("Establishing connection with S3 client for masterKeyValueFilePath for key : " + fileName);
		//AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
		logger.log("Connection established with S3 client for masterKeyValueFilePath bucket :" + bucketName);
		S3Object s3Object = s3Client.getObject(new GetObjectRequest(bucketName, fileName));
		logger.log("Reading object from S3 client for masterKeyValueFilePath");
		S3ObjectInputStream s3is = s3Object.getObjectContent();

		String searchKeywordString = IOUtils.toString(s3is);
		JSONParser parser = new JSONParser();
		JSONObject json = (JSONObject) parser.parse(searchKeywordString);
		logger.log("JSON object recieved is : "+ json.toString());
		return json;
	}


}
