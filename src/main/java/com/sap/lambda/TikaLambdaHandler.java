package com.sap.lambda;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.elasticsearch.action.index.IndexResponse;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.xml.sax.SAXException;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.sap.elastic.AWSElasticIndexer;
import com.sap.exception.IgnoreFileTypeException;
import com.sap.exception.RequestValidationException;
import com.sap.util.SearchKeywordsReader;
import com.sap.util.SearchTextMatcher;

@SuppressWarnings("unchecked")
public class TikaLambdaHandler implements RequestHandler<JSONObject, JSONObject> {

	public JSONObject handleRequest(JSONObject s3BulkPayload, Context context) {
    	LambdaLogger logger = context.getLogger();
    	logger.log(" + + + + + + + + received request for payload  --> " + s3BulkPayload);
        JSONArray resultArr = new JSONArray();
        Tika tika = new Tika();
        JSONObject response = new JSONObject();
        tika.setMaxStringLength(100*1024*1024);
        String taskId = "0";
        boolean ifAllFileTypeRequiredToRead = false;
        try {
        	List<LinkedHashMap<String, String>> tasks = (List<LinkedHashMap<String, String>>) s3BulkPayload.get("tasks");
        	List<String> validExtTypeList = new ArrayList<String>();
        	List<String> excludedFileExtListForContent = new ArrayList<String>();
        	String validExtType = System.getenv("validExtType");
        	String excludedFileExtForContent = System.getenv("excludedFileExtForContent");
        	if(validExtType!=null) {
        		validExtTypeList = Arrays.asList(validExtType.trim().split(","));
        	}
        	if(excludedFileExtForContent!=null) {
        		excludedFileExtListForContent = Arrays.asList(excludedFileExtForContent.trim().split(","));
        	}
        	if(validExtTypeList ==null || validExtTypeList.contains("NONE")) {
        		throw new IgnoreFileTypeException("All file type ignored. Found NONE in valid Extension Type parameters");
        	}else {
        		if(validExtTypeList.contains("NONE")) {
        			ifAllFileTypeRequiredToRead = true;
        		}
	        	for (LinkedHashMap<String, String> taskObj : tasks) {
	        		taskId = taskObj.get("taskId").toString();
					String bucket = taskObj.get("s3BucketArn").toString();
					String[] splitedArr = bucket.split(":");
					String bucketName = splitedArr[splitedArr.length-1];
					String key = URLDecoder.decode(taskObj.get("s3Key").toString().replace('+', ' '), "UTF-8");
					String filename = key;
					//filename = validateFileType(validExtTypeList, key, filename, logger, ifAllFileTypeRequiredToRead);
					String fileExtType = "DOC";
					if(key.contains("/")) {
						String[] filenameSeperatorArr = key.split("/");
						filename = filenameSeperatorArr[filenameSeperatorArr.length-1];
						if(filename.contains(".")) {
							fileExtType = filename.substring(filename.lastIndexOf('.')+1);
							logger.log("fileExtType is : "+ fileExtType);
						}else {
							throw new RequestValidationException("Invalid File Type : "+ fileExtType);
						}					
					}  
					if(!validExtTypeList.contains(fileExtType.toUpperCase().trim()) && (!ifAllFileTypeRequiredToRead)) {
						throw new IgnoreFileTypeException("File Extension Ignored : " + fileExtType);
					}
					
					S3ObjectInputStream s3is = getS3InputStream(logger, bucketName, key);
		            ByteArrayOutputStream outStream = new ByteArrayOutputStream(); 
		            byte[] read_buf = new byte[1024];
		            int read_len = 0;
		            while ((read_len = s3is.read(read_buf)) > 0) {
		            	outStream.write(read_buf, 0, read_len);
		            }
		            BodyContentHandler handler = new BodyContentHandler(-1);
		            AutoDetectParser parser = new AutoDetectParser();
		            Metadata metadata = new Metadata();
		            ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray()); 
		            try (InputStream stream = inStream){//s3Object.getObjectContent()) {
		                parser.parse(stream, handler, metadata);
		                String fileContent = "EMPTY_FILE_CONTENT";
		                if(handler.toString()!= null && handler.toString()!=""){
		                	fileContent = handler.toString();
		                }
		    			JSONObject esIndexerDocument =  new JSONObject();;
		    			esIndexerDocument.put("_metadata", prepareMetadataJSON(key, filename, metadata));
		    			if(excludedFileExtListForContent.contains(fileExtType)) {
		    				esIndexerDocument.put("_tags", prepareTagJSON(fileContent,bucketName, logger));
		    			}
		    			
		    			esIndexerDocument.put("_content", fileContent);
		    			IndexResponse indexResponse = AWSElasticIndexer.indexDocument(esIndexerDocument, logger);
		    			prepareIndexerResponse(response, taskId, indexResponse);
		    			resultArr.add(response);
		            } catch (SAXException e) {
						e.printStackTrace();
					}
		            s3is.close();
		            outStream.close(); 
				}
        	}
        } catch (RequestValidationException e) {
        	response.put("taskId", taskId);
        	response.put("resultCode", "PermanentFailure");
			response.put("resultString", e.getMessage());
			resultArr.add(response);
        } catch (IgnoreFileTypeException e) {
        	response.put("taskId", taskId);
        	response.put("resultCode", "Succeeded");
			response.put("resultString", e.getMessage());
			resultArr.add(response);
        }catch (Exception e) {
            logger.log("Exception: " + e.getLocalizedMessage());
            throw new RuntimeException(e);
        }
        JSONObject responseJSON = prepareResponseJSON(s3BulkPayload, logger, resultArr);
        return responseJSON;
    }

	
	private void prepareIndexerResponse(JSONObject response, String taskId, IndexResponse indexResponse) {
		response.put("taskId", taskId);
		if(indexResponse!=null) {
			response.put("resultCode", "Succeeded");
			response.put("resultString", indexResponse.getResult());
		}else {
			response.put("resultCode", "PermanentFailure");
			response.put("resultString", "Failed");
		}
	}

	private S3ObjectInputStream getS3InputStream(LambdaLogger logger, String bucketName, String key) {
		String s3AccessKeyForContentFile = System.getenv("s3_access_key_content_file");
		String s3SecretKeyForContentFile = System.getenv("s3_secret_key_content_file");
		
		BasicAWSCredentials awsCreds = new BasicAWSCredentials(s3AccessKeyForContentFile, s3SecretKeyForContentFile);
		AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
		                        .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
		                        .build();
		
		
		logger.log("Establishing connection with S3 client for key : " + key);
		//AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
		logger.log("Connection established with S3 client for bucket : "+ bucketName);
		S3Object s3Object = s3Client.getObject(new GetObjectRequest(bucketName, key));
		logger.log("Reading s3 object for : " +bucketName + " and " + key);
		S3ObjectInputStream s3is = s3Object.getObjectContent();
		logger.log("Initiate S3 object reading via stream");
		return s3is;
	}
	
	private JSONObject prepareTagJSON(String fileContent, String bucketName, LambdaLogger logger) throws Exception {
		JSONObject tagJSON = new JSONObject();
		JSONObject searchKeywordJson = SearchKeywordsReader.getKeyValueMap(bucketName, logger);
		JSONArray arr = null;
		
		if(searchKeywordJson!=null) {
			Set<String> searchKeys = searchKeywordJson.keySet();
			for (String searchKey : searchKeys) {
				List<String> searchValues = (List<String>) searchKeywordJson.get(searchKey);
				for(String searchString : searchValues){					
					if(SearchTextMatcher.matchSearchText(searchString.trim(), fileContent)) {
						if(tagJSON.get(searchKey)!=null) {
							((JSONArray)tagJSON.get(searchKey)).add(searchString);
						}else {
							arr = new JSONArray();
							arr.add(searchString.trim());
							tagJSON.put(searchKey, arr);
						}
					}
				}
			}
		}
		return tagJSON;
	}

	private JSONObject prepareMetadataJSON(String key, String filename, Metadata metadata) {
		JSONObject metadataJSON = new JSONObject();
		metadataJSON.put("FileName", filename);
		metadataJSON.put("FilePath", key);
		//metadataJSON.put("File Content", fileContent);
		getMetaDataValue("Author", metadata,metadataJSON, "Author");
		getMetaDataValue("ContentType", metadata,metadataJSON, "Content-Type");
		getMetaDataValue("CreationDatetime", metadata,metadataJSON, "Creation-Date");
		getMetaDataValue("LastModifiedDateTime", metadata,metadataJSON, "Modified-Date");
		return metadataJSON;
	}

	private JSONObject prepareResponseJSON(JSONObject s3BulkPayload, LambdaLogger logger, JSONArray resultArr) {
		JSONObject responseJSON = new JSONObject();
        responseJSON.put("invocationSchemaVersion", s3BulkPayload.get("invocationSchemaVersion"));
        responseJSON.put("treatMissingKeysAs", "PermanentFailure");
        responseJSON.put("invocationId", s3BulkPayload.get("invocationId"));
        responseJSON.put("results", resultArr);
        logger.log(" ****************************** returned JSON response is  : " + responseJSON);
		return responseJSON;
	}

	/*private String validateFileType(List<String> validExtTypeList, String key, String filename, LambdaLogger logger, 
			boolean ifAllFileTypeRequiredToRead) throws RequestValidationException, IgnoreFileTypeException {
		String fileExtType = "DOC";
		if(key.contains("/")) {
			String[] filenameSeperatorArr = key.split("/");
			filename = filenameSeperatorArr[filenameSeperatorArr.length-1];
			if(filename.contains(".")) {
				fileExtType = filename.substring(filename.lastIndexOf('.')+1);
				logger.log("fileExtType is : "+ fileExtType);
			}else {
				throw new RequestValidationException("Invalid File Type : "+ fileExtType);
			}					
		}  
		if(!validExtTypeList.contains(fileExtType.toUpperCase().trim()) && (!ifAllFileTypeRequiredToRead)) {
			throw new IgnoreFileTypeException("File Extension Ignored : " + fileExtType);
		}
		return filename;
	}*/
    
	private static void getMetaDataValue(String value, Metadata metadata, JSONObject metajson, String key) {
		if(value!=null || metadata.get(value) != null) {
			metajson.put(key, metadata.get(value));
		}
	}
}

