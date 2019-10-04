package com.cvise.output.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cvise.output.exception.FileReadException;
import com.cvise.output.exception.HttpFileTransferFailed;
import com.cvise.output.exception.InternalHttpRequestFailed;

@Service
public class OutputService {
	@Value("${user.home}")
    private String localFilesReferenceDir;
    @Value("${user.local.static.file.path}")
	private String staticFilePath;
	@Value("${user.local.static.file.endpoint}")
	private String staticFileEndpoint;
    
	/**Returns String of URL to static file location by moving local file to static files directory.
	 * 
	 * @param path String of file path to read from.
	 * @param request HttpServletRequest to construct URL from.
	 * @return String of URL to static output file.
	 */
	public String getStaticFileLocationFromFile(String path, HttpServletRequest request) {
    	String baseUrl = getStaticFilesUrlFromRequest(request);
    	String targetFileName = UUID.randomUUID().toString() + ".txt";
    	String targetPathString = staticFilePath + targetFileName;
        Path sourcePath = FileSystems.getDefault().getPath(path);
    	Path targetPath = FileSystems.getDefault().getPath(targetPathString);
    	try {
    		Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
	    	return baseUrl + targetFileName;
    	} catch (Exception e) {
            e.printStackTrace();
            throw new FileReadException(
					"Can't move file " + path
					+ " to " + targetPathString
					+ ". Please try again!");
        }
    }
	
	/**Returns String content of a file.
	 * 
	 * @param path String of file path to read from.
	 * @return Strong of text file content.
	 */
    public String getTextFromFile(String path) {
    	try {
			String content = new String (Files.readAllBytes(Paths.get(path)));
			return content;
		} catch (IOException e) {
			e.printStackTrace();
			throw new FileReadException(
					"Can't read content from file " + path
	                + " to String. Please try again!");
		}
    }
    
    /**Returns List of String from a text file, split by newline.
     * 
     * @param path String of file path to read from.
     * @return List of String of text file content, split by newline.
     */
    public List<String> getListFromFile(String path) {
    	try (Stream<String> lines = Files.lines(Paths.get(path))) {
    	    List<String> list = lines.collect(Collectors.toList());
    	    return list;
		} catch (IOException e) {
			e.printStackTrace();
			throw new FileReadException(
					"Can't read content from file " + path
	                + " to Lis<String>. Please try again!");
		}
    }
	
	/**Upload file from a URL location that was generated by
     * an API endpoint to a local location.
     * 
     * @param apiUrl String of API endpoint to make http request
     * 		  for the file location.
     * @param requestMethod String of REST verb to make the http
     * 		  request with.
     * @param contentType String of MIME of expected result from
     * 		  the http request. In this case, the MIME type should
     * 		  be "text/plain" since a String of URL of file location
     * 		  is expected from the API call.
     * @return String of path to local file 
     */
    public String uploadFileFromApiRequest(String apiUrl,
    									   String requestMethod,
    									   String contentType) {
    	String urlToDataFile = getStringFromHttpRequest(
    			apiUrl,
    			requestMethod,
    			contentType);
    	String pathToLocalFile = uploadFileFromLink(urlToDataFile);
    	return pathToLocalFile;
    }

    /**Make an http request to an URL that must respond with a String.
     * 
     * @param url String of API endpoint to make http request.
     * @param requestMethod String of REST verb to make the request with.
     * @param contentType String of MIME of expected return content type.
     * @return String response from the http request.
     */
    public String getStringFromHttpRequest(String url,
    									   String requestMethod,
    									   String contentType) {
    	try {
    		// Connect to the endpoint
    		URL urlObj= new URL(url);
        	HttpURLConnection con = (HttpURLConnection) urlObj.openConnection();
        	con.setRequestMethod(requestMethod);
        	con.setRequestProperty("Content-Type", contentType);
        	
        	// Read result from the endpoint
        	InputStream conStream = con.getInputStream();
        	BufferedReader in = new BufferedReader(new InputStreamReader(conStream));
    		String inputLine;
    		StringBuffer content = new StringBuffer();
    		while ((inputLine = in.readLine()) != null) {
    		    content.append(inputLine);
    		}
    		
    		// Clean up
    		in.close();
    		con.disconnect();
    		
    		// Get the result
    		String urlToFile = content.toString();
    		return urlToFile;    		
    	} catch (Exception e) {
            e.printStackTrace();
            throw new InternalHttpRequestFailed(
            	"Failed to make http request to " + url
            	+ " with request mehod " + requestMethod
            	+ " and content type " + contentType
            	+ ". Please try again!");
        }
    }
    
    /**Efficiently upload file from a given URL to a local location
     * by using the stream transfer function.  
     * 
     * @param url String of URL to data file.
     * @return String of path to local file 
     */
    public String uploadFileFromLink(String url) {
    	String localFileName = UUID.randomUUID().toString() + ".txt";
		String localFilePath = localFilesReferenceDir + File.separator + localFileName;
    	try {
    		// Connect to the external and local file location
    		InputStream conStream = new URL(url).openStream();
    		FileOutputStream fileOutputStream = new FileOutputStream(localFilePath);
    		
    		// Stream the file to local path
    		ReadableByteChannel readableByteChannel = Channels.newChannel(conStream);
			fileOutputStream.getChannel()
			  .transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
			
			// Clean up
			fileOutputStream.close();
			
			// Return path if transfer happens properly
			return localFilePath;
		} catch (IOException e) {
			e.printStackTrace();
			throw new HttpFileTransferFailed(
	            	"Failed to download file from " + url
	            	+ " to " + localFilePath
	            	+ ". Please try again!");
		}
    }
    
	
	/**Generate URL string to the location to static files being hosted.
	 * 
	 * @param request HttpServletRequest from the controller to extract info from.
	 * @return String of URL pointing to static files location.
	 */
	public String getStaticFilesUrlFromRequest(HttpServletRequest request) {
		return String.format("%s://%s:%d%s",
			request.getScheme(),
			request.getServerName(),
			request.getServerPort(),
			staticFileEndpoint);
	}
}