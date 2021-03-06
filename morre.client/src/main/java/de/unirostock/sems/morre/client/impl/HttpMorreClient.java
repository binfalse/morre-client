package de.unirostock.sems.morre.client.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import de.unirostock.sems.morre.client.FeatureSet;
import de.unirostock.sems.morre.client.Morre;
import de.unirostock.sems.morre.client.MorreCrawlerInterface;
import de.unirostock.sems.morre.client.QueryType;
import de.unirostock.sems.morre.client.dataholder.AnnotationResult;
import de.unirostock.sems.morre.client.dataholder.CrawledModel;
import de.unirostock.sems.morre.client.dataholder.ModelResult;
import de.unirostock.sems.morre.client.dataholder.PersonResult;
import de.unirostock.sems.morre.client.dataholder.PublicationResult;
import de.unirostock.sems.morre.client.exception.MorreClientException;
import de.unirostock.sems.morre.client.exception.MorreCommunicationException;
import de.unirostock.sems.morre.client.exception.MorreException;

public class HttpMorreClient implements Morre, MorreCrawlerInterface, Serializable {

	private static final long serialVersionUID = 6215972631957486031L;

	private final Log log = LogFactory.getLog( getClass() );

	private URL morreUrl = null;
	private URL queryUrl = null;
	
	private HttpClient httpClient = null;
	private Gson gson = null;

	private Type singleListType;
	//	private Type completeType;
	private Type featureListType;
	private Type modelResultType;
	private Type personResultType;
	private Type publicationResultType;
	private Type annotationResultType;
	private Type crawledModelType;
	private Type singleMapType;

	private static final String REST_URL_QUERY = "query/";
	private static final String REST_URL_CRAWLER = "model_crawler_service/";
	private static final String REST_URL_UPDATE = "model_update_service/";
	
	private static final String KEY_KEYWORDS = "keywords";
	private static final String KEY_FEATURES = "features";
	private static final String KEY_SINGLE_KEYWORD = "keyword";
	private static final String AGGREGATION_TYPE = "aggregationType";
	private static final String RANKERS_WEIGHTS = "rankersWeights";

	private static final String ERROR_KEY_RESULTS = "#Results";
	private static final String ERROR_KEY_EXCEPTION = "Exception";

	// ----

	private static final String SERVICE_GET_MODEL_HISTORY = REST_URL_CRAWLER + "get_model_history";
	private static final String SERVICE_GET_MODEL_VERSION = REST_URL_CRAWLER + "get_model_version";
	private static final String SERVICE_GET_LATEST_MODEL = REST_URL_CRAWLER + "get_model";
	private static final String SERVICE_ADD_MODEL = REST_URL_UPDATE + "add_model";
	private static final String SERVICE_ADD_MODEL_VERSION = REST_URL_UPDATE + "add_model_version";
	private static final String SERVICE_DELETE_MODEL = REST_URL_UPDATE + "delete_model";

	private static final String SKEY_FILEID = "fileId";
	private static final String SKEY_VERSIONID = "versionId";
	private static final String SKEY_EXCEPTION = "Exception";

	public HttpMorreClient(String morreUrl) throws MalformedURLException {
		// define urls
		this.morreUrl = new URL(morreUrl);
		this.queryUrl = new URL(this.morreUrl, REST_URL_QUERY);

		httpClient = HttpClientBuilder.create().build();
		gson = new Gson();

		//		completeType = new TypeToken<List<Map<String, JsonElement>>>(){}.getType();
		singleListType = new TypeToken<List<String>>(){}.getType();
		featureListType = new TypeToken<List<String>>(){}.getType();

		modelResultType = new TypeToken<List<ModelResult>>(){}.getType();
		personResultType = new TypeToken<List<PersonResult>>(){}.getType();
		publicationResultType = new TypeToken<List<PublicationResult>>(){}.getType();
		annotationResultType = new TypeToken<List<AnnotationResult>>(){}.getType();

		crawledModelType = new TypeToken<CrawledModel>(){}.getType();
		singleMapType = new TypeToken<Map<String, String>>(){}.getType();
	}

	@Override
	public List<ModelResult> modelQuery(String query) throws MorreClientException, MorreCommunicationException, MorreException {
		return doSimpleModelQuery(QueryType.MODEL_QUERY, query);
	}
	
	@Override
	public List<ModelResult> aggregatedModelQuery(String query, String aggregationType, String rankersWeights) throws MorreClientException, MorreCommunicationException, MorreException {
		return doSimpleAggregatedModelQuery(QueryType.AGGREGATED_MODEL_QUERY, query, aggregationType, rankersWeights);
	}

	@Override
	public List<String> getQueryFeatures(String queryType) throws MorreException, MorreClientException, MorreCommunicationException {

		try {
			HttpGet request = new HttpGet( new URL(queryUrl, queryType).toString() );
			HttpResponse response = httpClient.execute(request);

			// reads in the result
			StringBuilder result = new StringBuilder();
			BufferedReader resultReader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
			String line = "";
			while ((line = resultReader.readLine()) != null) {
				//append              
				result.append(line);
			}

			List<String> featureList = gson.fromJson(result.toString(), featureListType);

			return featureList;
		} catch (JsonSyntaxException e) {
			throw new MorreException("Can not parse the FeatureSet List!", e);
		} catch (MalformedURLException e) {
			// Wrong formatted URL.
			throw new MorreClientException("Exception while building the request url", e);
		} catch (IOException e) {
			// Something went wrong with the communication
			throw new MorreCommunicationException("Error while HTTP Request.", e);
		}


	}

	@Override
	public List<ModelResult> doModelQuery(String queryType, FeatureSet features) throws MorreClientException, MorreCommunicationException, MorreException {
		// perform the query
		String resultString = performQuery(queryType, features);
		return parseQueryResult(resultString, modelResultType);
	}

	@Override
	public List<ModelResult> doSimpleModelQuery(String queryType, String keyword) throws MorreException ,MorreClientException ,MorreCommunicationException {
		// perform the query
		String resultString = performSimpleQuery(queryType, keyword);
		return parseQueryResult(resultString, modelResultType);
	}
	
	@Override
	public List<ModelResult> doSimpleAggregatedModelQuery(String queryType, String keyword, String aggregationType, String rankersWeights) throws MorreException ,MorreClientException ,MorreCommunicationException {
		// perform the query
		String resultString = performSimpleAggregatedQuery(queryType, keyword, aggregationType, rankersWeights);
		return parseQueryResult(resultString, modelResultType);
	}

	@Override
	public List<PersonResult> doPersonQuery(FeatureSet features) throws MorreClientException, MorreCommunicationException, MorreException {
		// perform the query
		String resultString = performQuery(QueryType.PERSON_QUERY, features);
		return parseQueryResult(resultString, personResultType);
	}

	@Override
	public List<AnnotationResult> doAnnotationQuery(String query) throws MorreClientException, MorreCommunicationException, MorreException {
		// perform the query
		String resultString = performSimpleQuery(QueryType.ANNOTATION_QUERY, query);
		return parseQueryResult(resultString, annotationResultType);
	}

	@Override
	public List<PublicationResult> doPublicationQuery(FeatureSet features) throws MorreException, MorreClientException, MorreCommunicationException {
		// perform the query
		String resultString = performQuery(QueryType.PUBLICATION_QUERY, features);
		return parseQueryResult(resultString, publicationResultType);
	}

	private <R> List<R> parseQueryResult( String resultString, Type parseType ) throws MorreClientException, MorreCommunicationException, MorreException {

		// Lets try to parse the shit out of it!

		List<R> result = null;
		try {
			// trying to parse the result correctly
			result = gson.fromJson(resultString, parseType);
		}
		catch (JsonSyntaxException e) {
			// **** first catch block ****
			// first attempt failed -> try to get a error message out of the result

			try {
				List<String> errorResult = gson.fromJson(resultString, featureListType);

				if( errorResult == null || errorResult.isEmpty() ) {
					// the errorResult List is empty
					throw new MorreException( "Empty result, no error description." );
				}				
				else if( errorResult.get(0).equals(ERROR_KEY_RESULTS) ) {
					// A result return. If the second value is null, the database could not find an entry.
					if( errorResult.get(1).equals("0") ) {
						// null for no entry found!
						// TODO think about the null return. Shouldn't we just return an empty List?
						return null;
					}

				}
				else if( errorResult.get(0).equals(ERROR_KEY_EXCEPTION) ) {
					// We've got a database exception! Let's throw it!
					if( errorResult.size() >= 2 )
						// there is a second parameter, specifying the exact error
						throw new MorreException( errorResult.get(1) );
					else
						// no explaining parameter, just an error.
						throw new MorreException();
				}
				else {
					// Something unknown was returned
					throw new MorreException( MessageFormat.format("Unknown Result was returned by MORRE: {0}", errorResult) );
				} 

			}
			catch (JsonSyntaxException e2) {
				System.out.println (resultString);
				// second attempt to parse failed, two. Now our fates rests in God's hands... (... or we just throw an exception)
				throw new MorreCommunicationException("Can not even parse the error message. Check for corrupt JSON!", e);
			}

			// **** first catch block ****
		}

		return result;
	}

	private String performQuery( String queryType, FeatureSet features ) throws MorreClientException, MorreCommunicationException {

		try {
			// Serialize the feature set

			Entry<List<String>, List<String>> separateLists = features.getFeatures();
			HashMap<String, JsonElement> complete = new HashMap<String, JsonElement>();

			// First parse the feature and value list
			complete.put( KEY_FEATURES, gson.toJsonTree( separateLists.getKey(), singleListType ) );
			complete.put( KEY_KEYWORDS, gson.toJsonTree( separateLists.getValue(), singleListType ) );

			String jsonFeatures = gson.toJson( complete );

			// generates the request
			HttpPost request = new HttpPost( new URL(queryUrl, queryType).toString() );
			// adds the json string as package
			request.setEntity( new StringEntity(jsonFeatures, ContentType.APPLICATION_JSON) );

			// execute!
			HttpResponse response = httpClient.execute(request);

			// reads in the result
			StringBuilder result = new StringBuilder();
			BufferedReader resultReader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
			String line = "";
			while ((line = resultReader.readLine()) != null) {
				//append              
				result.append(line);
			}

			return result.toString();
		} catch (MalformedURLException e) {
			// Wrong formatted URL. We can definitely blame the library user for this.
			// Exception the awesome library developer uses it by himself, than we have to blame someone else... ;)
			throw new MorreClientException("Exception while building the request url", e);
		} catch (IOException e) {
			// Something went wrong with the communication
			throw new MorreCommunicationException("Error while HTTP Request.", e);
		}
	}

	private String performSimpleQuery( String queryType, String keyword ) throws MorreClientException, MorreCommunicationException {

		try {
			// Serialize the feature set

			HashMap<String, String> parameter = new HashMap<String, String>();

			// Put in the Keyword
			parameter.put(KEY_SINGLE_KEYWORD, keyword);
			String jsonFeatures = gson.toJson( parameter );

			// generates the request
			String requestUrl = new URL(queryUrl, queryType).toString();
			HttpPost request = new HttpPost( requestUrl );
			// adds the json string as package
			request.setEntity( new StringEntity(jsonFeatures, ContentType.APPLICATION_JSON) );

			// execute!
			HttpResponse response = httpClient.execute(request);

			// reads in the result
			StringBuilder result = new StringBuilder();
			BufferedReader resultReader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
			String line = "";
			while ((line = resultReader.readLine()) != null) {
				//append              
				result.append(line);
			}

			return result.toString();
		} catch (MalformedURLException e) {
			// Wrong formatted URL. We can definitely blame the library user for this.
			// Exception the awesome library developer uses it by himself, than we have to blame someone else... ;)
			throw new MorreClientException("Exception while building the request url", e);
		} catch (IOException e) {
			// Something went wrong with the communication
			throw new MorreCommunicationException("Error while HTTP Request.", e);
		}

	}
	
	private String performSimpleAggregatedQuery( String queryType, String keyword, String aggregationType, String rankersWeights ) throws MorreClientException, MorreCommunicationException {

		try {
			// Serialize the feature set

			HashMap<String, String> parameter = new HashMap<String, String>();

			// Put in the Keyword
			parameter.put(KEY_SINGLE_KEYWORD, keyword);
			parameter.put(AGGREGATION_TYPE, aggregationType);
			parameter.put(RANKERS_WEIGHTS, rankersWeights);
			String jsonFeatures = gson.toJson( parameter );

			// generates the request
			String requestUrl = new URL(queryUrl, queryType).toString();
			HttpPost request = new HttpPost( requestUrl );
			// adds the json string as package
			request.setEntity( new StringEntity(jsonFeatures, ContentType.APPLICATION_JSON) );

			// execute!
			HttpResponse response = httpClient.execute(request);

			// reads in the result
			StringBuilder result = new StringBuilder();
			BufferedReader resultReader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
			String line = "";
			while ((line = resultReader.readLine()) != null) {
				//append              
				result.append(line);
			}

			return result.toString();
		} catch (MalformedURLException e) {
			// Wrong formatted URL. We can definitely blame the library user for this.
			// Exception the awesome library developer uses it by himself, than we have to blame someone else... ;)
			throw new MorreClientException("Exception while building the request url", e);
		} catch (IOException e) {
			// Something went wrong with the communication
			throw new MorreCommunicationException("Error while HTTP Request.", e);
		}

	}

	// ---------------------------------------------------------------------------------------------------------------------

	@Override
	public List<String> getModelHistory(String fileId) throws MorreClientException, MorreCommunicationException, MorreException {
		Map<String, String> parameter = new HashMap<String, String>();
		parameter.put(SKEY_FILEID, fileId);

		String result = performServiceQuery(SERVICE_GET_MODEL_HISTORY, parameter);
		return parseServiceResult(result, singleListType);
	}

	@Override
	public CrawledModel getModelVersion(String fileId, String versionId) throws MorreClientException, MorreCommunicationException, MorreException {
		Map<String, String> parameter = new HashMap<String, String>();
		parameter.put(SKEY_FILEID, fileId);
		parameter.put(SKEY_VERSIONID, versionId);

		String result = performServiceQuery(SERVICE_GET_MODEL_VERSION, parameter);
		return parseServiceResult(result, crawledModelType);
	}

	@Override
	public CrawledModel getLatestModelVersion(String fileId) throws MorreClientException, MorreCommunicationException, MorreException {
		Map<String, String> parameter = new HashMap<String, String>();
		parameter.put(SKEY_FILEID, fileId);

		String result = performServiceQuery(SERVICE_GET_LATEST_MODEL, parameter);
		System.out.println(result);
		return parseServiceResult(result, crawledModelType);
	}

	@Override
	public boolean addModel(CrawledModel model) throws MorreClientException, MorreCommunicationException, MorreException {

		String result = performServiceQuery(SERVICE_ADD_MODEL_VERSION, model, crawledModelType);
		log.trace(result);
		
		System.out.println("----");
		System.out.println(result);
		System.out.println("----");
		log.debug(result);
		
		Map<String, String> parsedResult = parseServiceResult(result, singleMapType);
		if( parsedResult != null && parsedResult.get("ok").toLowerCase().equals("true") )
			return true;
		else
			return false;
	}

	private <R> String performServiceQuery( String queryType, R parameter ) throws MorreClientException, MorreCommunicationException {
		return performServiceQuery(queryType, parameter, null);
	}
	
	private <R> String performServiceQuery( String queryType, R parameter, Type parameterType ) throws MorreClientException, MorreCommunicationException {

		try {

			// serialize the parameter
			String jsonFeatures = null;
			if( parameterType != null )
				jsonFeatures = gson.toJson(parameter, parameterType);
			else
				jsonFeatures = gson.toJson( parameter );

			// generates the request
			String requestUrl = new URL(morreUrl, queryType).toString();
			HttpPost request = new HttpPost( requestUrl );
			request.addHeader( "Accept", ContentType.APPLICATION_JSON.toString());
			// adds the json string as package
			System.out.println(jsonFeatures);
			log.trace(jsonFeatures);
			request.setEntity( new StringEntity(jsonFeatures, ContentType.APPLICATION_JSON) );

			// execute!
			HttpResponse response = httpClient.execute(request);

			// reads in the result
			StringBuilder result = new StringBuilder();
			BufferedReader resultReader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
			String line = "";
			while ((line = resultReader.readLine()) != null) {
				//append              
				result.append(line);
			}

			return result.toString();
		} catch (MalformedURLException e) {
			// Wrong formatted URL. We can definitely blame the library user for this.
			// Except the awesome library developer uses it by himself, than we have to blame someone else... ;)
			throw new MorreClientException("Exception while building the request url", e);
		} catch (IOException e) {
			// Something went wrong with the communication
			throw new MorreCommunicationException("Error while HTTP Request.", e);
		}

	}

	private <R> R parseServiceResult( String result, Type resultType ) throws MorreCommunicationException, MorreException {

		R resultObj = null;
		try {
			// first of all: try to parse the result correctly
			resultObj = gson.fromJson(result, resultType);
			if( resultObj instanceof Map<?, ?> ) {
				// The returnType is a Map, maybe it is full of exception, lets check that
				if( ((Map<?, ?>) resultObj).containsKey(SKEY_EXCEPTION) == true )
					// the map contains an exception element -> lets analyse it!
					analyseServiceException((Map<?, ?>) resultObj); 
			}
		} catch (JsonSyntaxException e) {
			// not the result we've expected. Maybe its an exception?
			Map<String, String> resultMap;
			try {
				resultMap = gson.fromJson(result, singleMapType);
				analyseServiceException(resultMap, e);
			}
			catch (JsonSyntaxException e2) {
				throw new MorreCommunicationException("Can not even parse the error message. Check for corrupt JSON! " + result, e);
			}

		}

		return resultObj;
	}

	private void analyseServiceException( Map<?, ?> resultMap ) throws MorreException {
		analyseServiceException(resultMap, null);
	}

	private void analyseServiceException( Map<?, ?> resultMap, Throwable e ) throws MorreException {

		if( resultMap.get(SKEY_EXCEPTION) != null && resultMap.get(SKEY_EXCEPTION) instanceof String ) {
			// result is a map, containing the exception field, which is a String
			String error = "Server-Side exception while request: " + (String) resultMap.get(SKEY_EXCEPTION);
			if( e != null )
				throw new MorreException( error, e );
			else
				throw new MorreException(error);
		}

	}

}
