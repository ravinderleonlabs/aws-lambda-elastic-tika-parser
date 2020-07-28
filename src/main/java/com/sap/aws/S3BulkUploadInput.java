package com.sap.aws;

import java.io.FileWriter;
import java.util.LinkedList;
import java.util.List;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.opencsv.CSVWriter;

public class S3BulkUploadInput {

	public static void getS3BulkUploadInput() throws Exception {
		String bucket = "tika-bucket";
		String filePath = "C:\\Ravinder\\Personal\\work\\poc-project\\software\\s3BulkUpload.csv";
		AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion("ap-south-1").build();

		String prefix = "Test-Bucket";
		List<String> files = getObjectsListFromS3(s3, bucket, prefix);
		for (String key : files) {
			System.out.println(key);
		}

		writeToCSV(files, filePath, bucket);

	}

	public static void writeToCSV(List<String> files, String path, String bucketName) throws Exception {
		CSVWriter csvWriter = new CSVWriter(new FileWriter(path));
		for (String fileName : files) {
			csvWriter.writeNext(new String[] { bucketName, fileName });
		}
		csvWriter.close();
	}

	public static List<String> getObjectsListFromS3(AmazonS3 s3, String bucket, String prefix) {
		final String delimiter = "/";
		if (!prefix.endsWith(delimiter)) {
			prefix = prefix + delimiter;
		}

		List<String> paths = new LinkedList<>();
		ListObjectsRequest request = new ListObjectsRequest().withBucketName(bucket).withPrefix(prefix);

		ObjectListing result;
		do {
			result = s3.listObjects(request);

			for (S3ObjectSummary summary : result.getObjectSummaries()) {
				// To make sure we are not adding a 'folder'
				if (!summary.getKey().endsWith(delimiter)) {
					paths.add(summary.getKey());
				}
			}
			request.setMarker(result.getMarker());
		} while (result.isTruncated());

		return paths;
	}

	public static void main(String[] args) throws Exception {
		
		//access-secret = dce6ezEDB/PzbCk+F8kjyZ8uOwTySh4VlyFiZ6yS;
		//access-key = AKIA5RPUC4OMKBCZQQNT;
		String bucket = "tika-bucket";
		String filePath = "C:\\Ravinder\\Personal\\work\\poc-project\\software\\s3BulkUpload.csv";
		AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion("ap-south-1").build();

		String prefix = "Test-Bucket";
		List<String> files = getObjectsListFromS3(s3, bucket, prefix);
		for (String key : files) {
			System.out.println(key);
		}

		writeToCSV(files, filePath, bucket);

	}

}
