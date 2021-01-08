package com.project.githubsearch;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.github.kevinsawicki.http.HttpRequest;
import com.project.githubsearch.model.MavenPackage;
import com.project.githubsearch.model.Query;
import com.project.githubsearch.model.ResolvedData;
import com.project.githubsearch.model.ResolvedFile;
import com.project.githubsearch.model.Response;
import com.project.githubsearch.model.SynchronizedData;
import com.project.githubsearch.model.SynchronizedFeeder;
import com.project.githubsearch.model.SynchronizedTypeSolver;
import com.project.githubsearch.model.GithubToken;

import org.json.JSONArray;
import org.json.JSONObject;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

/**
 * Github Search Engine
 *
 */
public class App {

    // run multiple token
    // please make sure that the number of thread is equal with the number of tokens
    private static final int NUMBER_THREADS = 3;
    private static final int NUMBER_CORE = 1;

    // parameter for the request
    private static final String PARAM_QUERY = "q"; //$NON-NLS-1$
    private static final String PARAM_PAGE = "page"; //$NON-NLS-1$
    private static final String PARAM_PER_PAGE = "per_page"; //$NON-NLS-1$

    // links from the response header
    private static final String META_REL = "rel"; //$NON-NLS-1$
    private static final String META_NEXT = "next"; //$NON-NLS-1$
    private static final String DELIM_LINKS = ","; //$NON-NLS-1$
    private static final String DELIM_LINK_PARAM = ";"; //$NON-NLS-1$

    // response code from github
    private static final int BAD_CREDENTIAL = 401;
    private static final int RESPONSE_OK = 200;
    private static final int ABUSE_RATE_LIMITS = 403;
    private static final int UNPROCESSABLE_ENTITY = 422;
    
    // number of needed file to be resolved
    private static final int MAX_RESULT = 1;

    // folder location to save the downloaded files and jars
    //TODO data location potentially needs to be changed so disk space isn't all used
    private static String DATA_LOCATION = "src/main/java/com/project/githubsearch/data/";
    private static final String JARS_LOCATION = "src/main/java/com/project/githubsearch/jars/";

    private static final String endpoint = "https://api.github.com/search/code";

    private static SynchronizedData synchronizedData = new SynchronizedData();
    private static SynchronizedFeeder synchronizedFeeder = new SynchronizedFeeder();
    private static ResolvedData resolvedData = new ResolvedData();
    private static SynchronizedTypeSolver synchronizedTypeSolver = new SynchronizedTypeSolver();

    private static Instant start;
    private static Instant currentTime;


    public static void main(String[] args) {
        //* Scanner parses query input supplied from console and assigns string to variable: input
        Scanner scanner = new Scanner(System.in);
        System.out.println("Please Input Your Query: ");
        String input = scanner.nextLine();
        scanner.close();
        ArrayList<Query> queries = parseQueries(input);

        if (queries.size() > 0) {
            printQuery(queries);
            initUniqueFolderToSaveData(queries);
            start = Instant.now();

            String javaProjects = "/Users/tenghao/GitHub/github-code-search/src/main/java/com/project/githubsearch/files/testing.txt";
            try (BufferedReader br = new BufferedReader(new FileReader(javaProjects))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String content;
                    try {
                        content = Files.readString(Paths.get(line));
                        //TODO - Process content
                        System.out.println(content);
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            catch (Exception e) {
                System.err.println(e.getMessage());
            } 




            // processQuery(queries);

            // for (int i = 0; i < resolvedData.getResolvedFiles().size(); i++) {
            //     System.out.println();
            //     System.out.println("URL: " + resolvedData.getResolvedFiles().get(i).getUrl());
            //     System.out.println("Path to File: " + resolvedData.getResolvedFiles().get(i).getPathFile());
            //     System.out.println("Line: " + resolvedData.getResolvedFiles().get(i).getLines());
            //     System.out.println("=== Snippet Codes ===");
            //     ArrayList<String> codes = getSnippetCode(resolvedData.getResolvedFiles().get(i).getPathFile(), resolvedData.getResolvedFiles().get(i).getLines());
            //     for (int j = 0; j < codes.size(); j++) {
            //         System.out.println(codes.get(j));
            //     }
            // }
        }
    }

    private static ArrayList<String> getSnippetCode(String pathFile, ArrayList<Integer> lines) {
        ArrayList<String> codes = new ArrayList<String>();

        int min, max, length;
        length = lines.size();
        if (length == 1) {
            min = max = lines.get(0).intValue();
        } else {
            min = lines.get(0).intValue();
            max = lines.get(0).intValue();
            for (int i = 1; i < length; i++) {
                if ( lines.get(i).intValue() < min) {
                    min = lines.get(i).intValue();
                }
                if ( lines.get(i).intValue() > max) {
                    max = lines.get(i).intValue();
                }
            }
        }

        BufferedReader reader;
        int i = 0;
        try {
            reader = new BufferedReader(new FileReader(pathFile));
            String line = reader.readLine();
            while (line != null) {
                i++;
                // System.out.println(line);

                if (i < (max + 5) && i > (min - 5)) {
                    codes.add(line);
                }
                // read next line
                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return codes;
    }

    private static void processQuery(ArrayList<Query> queries) {
        
        String query = prepareQuery(queries);

        int lower_bound, upper_bound, page, per_page_limit;
        lower_bound = 0;
        upper_bound = 384000;
        page = 1;
        per_page_limit = 30;

        //* response is now the proper Response object with params obtained from GH query
        Response response = handleCustomGithubRequest(query, lower_bound, upper_bound, page, per_page_limit);
        String nextUrlRequest;
        if (response.getTotalCount() == 0) {
            System.out.println("No item match with the query");
        } else {
            JSONArray item = response.getItem();
            nextUrlRequest = response.getNextUrlRequest();

            Queue<String> data = new LinkedList<>();
            for (int it = 0; it < item.length(); it++) {
                JSONObject instance = new JSONObject(item.get(it).toString());
                data.add(instance.getString("html_url"));
            }

            
            int id = 0;

            // this chunk seems to be for adding stuff progressively to the queue as cores free up
            while (resolvedData.getResolvedFiles().size() < MAX_RESULT) {
                if (data.size() < (2 * NUMBER_CORE)) {
                    response = handleGithubRequestWithUrl(nextUrlRequest);
                    item = response.getItem();
                    nextUrlRequest = response.getNextUrlRequest();
                    for (int it = 0; it < item.length(); it++) {
                        JSONObject instance = new JSONObject(item.get(it).toString());
                        data.add(instance.getString("html_url"));
                    }
                }

                // System.out.println("=====================");
                // System.out.println("Without multi-threading");
                // System.out.println("=====================");
                id = id + 1;
                String htmlUrl = data.remove();
                System.out.println();
                System.out.println("ID: " + id);
                System.out.println("File Url: " + htmlUrl);
                downloadAndResolveFile(id, htmlUrl, queries);
                

                // System.out.println("=====================");
                // System.out.println("Multi-threading start");
                // System.out.println("=====================");

                // ExecutorService executor = Executors.newFixedThreadPool(NUMBER_THREADS);

                // for (int i = 0; i < NUMBER_CORE; i++) {
                //     String htmlUrl = data.remove();
                //     id = id + 1;
                //     System.out.println("id: " + id);
                //     System.out.println("html url: " + htmlUrl);
                //     Runnable worker = new RunnableResolver(id, htmlUrl, queries);
                //     executor.execute(worker);
                // }

                // executor.shutdown();
                // // Wait until all threads are finish
                // while (!executor.isTerminated()) {}

                // System.out.println("===================");
                // System.out.println("Multi-threading end");
                // System.out.println("===================");

            }
        }

    }
    
    public static void downloadAndResolveFile (int id, String htmlUrl, ArrayList<Query> queries) {
        boolean isDownloaded = downloadFile(htmlUrl, id);
        if (isDownloaded) {
            ResolvedFile resolvedFile = resolveFile(id, queries);
            if (!resolvedFile.getPathFile().equals("")) {
                currentTime = Instant.now();
                long timeElapsed = Duration.between(start, currentTime).toMillis();
                long minutes = (timeElapsed / 1000) / 60;
                long seconds = (timeElapsed / 1000) % 60;
                long ms = (timeElapsed % 1000);
                System.out.println(
                    "Elapsed time from start: " + minutes + " minutes " + seconds + " seconds " + ms + "ms");
                //TODO the problem now is how to link the snippet with the library we found it in
                resolvedFile.setUrl(htmlUrl);
                System.out.println("URL: " + resolvedFile.getUrl());
                System.out.println("Path to File: " + resolvedFile.getPathFile());
                System.out.println("Line: " + resolvedFile.getLines());
                System.out.println("Snippet Codes: ");
                ArrayList<String> codes = getSnippetCode(resolvedFile.getPathFile(), resolvedFile.getLines());
                for (int j = 0; j < codes.size(); j++) {
                    System.out.println(codes.get(j));
                }
                resolvedData.add(resolvedFile);
            }
        }
    }

    public static class RunnableResolver implements Runnable {
        private final int id;
        private final String htmlUrl;
        private final ArrayList<Query> queries;

        RunnableResolver(int id, String htmlUrl, ArrayList<Query> queries) {
            this.id = id;
            this.htmlUrl = htmlUrl;
            this.queries = queries;
        }

        @Override
        public void run() {
            downloadAndResolveFile(id, htmlUrl, queries);
        }
    }

    private static boolean downloadFile(String htmlUrl, int fileId){
    //* downloads file to DATA_LOCATION folder from GH link
        // convert html url to downloadable url
        // based on my own analysis
        String downloadableUrl = convertHTMLUrlToDownloadUrl(htmlUrl);

        // using it to make a unique name
        // replace java to txt for excluding from maven builder
        //! this filename is probaby important to align everything
        //! "src/main/java/com/project/githubsearch/data/files/<fileId>.txt"
        //! id increments by 1 for every new file
        String fileName = fileId + ".txt";

        // System.out.println();
        // System.out.println("Downloading the file: " + (fileId));
        // System.out.println("HTML Url: " + htmlUrl);

        boolean finished = false;

        try {
            // download file from url
            URL url;
            url = new URL(downloadableUrl);
            ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream());
            String pathFile = new String(DATA_LOCATION + "files/" + fileName);
            FileOutputStream fileOutputStream = new FileOutputStream(pathFile);
            fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
            fileOutputStream.close();
            finished = true;
        } catch (FileNotFoundException e) {
            System.out.println("Can't download the github file");
            System.out.println("File not found!");
        } catch (MalformedURLException e) {
            System.out.println("Malformed URL Exception while downloading!");
            e.printStackTrace();
        } catch (IOException e) {
            System.out.println("Can't save the downloaded file");
        }

        return finished;
    }

    private static ResolvedFile resolveFile(int fileId, ArrayList<Query> queries) {
        //TODO maybe need to set pathFile at point when processQuery is called already
        String pathFile = new String(DATA_LOCATION + "files/" + fileId + ".txt");

        File file = new File(pathFile);

        ArrayList<String> snippetCodes = new ArrayList<String>();
        ArrayList<Integer> lines = new ArrayList<Integer>();

        ResolvedFile resolvedFile = new ResolvedFile(queries, "setUrl", "", lines, snippetCodes);
        // System.out.println();
        try {
            List<String> addedJars = getNeededJars(file);
            for (int i = 0; i < addedJars.size(); i++) {
                try {
                    TypeSolver jarTypeSolver = JarTypeSolver.getJarTypeSolver(addedJars.get(i));
                    synchronizedTypeSolver.add(jarTypeSolver);
                } catch (Exception e) {
                    System.out.println("=== Package corrupt! ===");
                    System.out.println("Corrupted jars: " + addedJars.get(i));
                    System.out.println("Please download the latest jar manually from maven repository!");
                    System.out.println("File location: " + file.toString());
                }
            }
            StaticJavaParser.getConfiguration().setSymbolResolver(new JavaSymbolSolver(synchronizedTypeSolver.getTypeSolver()));
            CompilationUnit cu;
            cu = StaticJavaParser.parse(file);

            ArrayList<Boolean> isMethodMatch = new ArrayList<Boolean>();
            ArrayList<Boolean> isResolved = new ArrayList<Boolean>();
            ArrayList<Boolean> isResolvedAndParameterMatch = new ArrayList<Boolean>();

            for (int i = 0; i < queries.size(); i++) {
                isMethodMatch.add(false);
                isResolved.add(false);
                isResolvedAndParameterMatch.add(false);
            }
            
            
            for (int i = 0; i < queries.size(); i++) {
                final int index = i;
                Query query = queries.get(index);
                ArrayList<MethodCallExpr> methodCallExprs = (ArrayList<MethodCallExpr>) cu.findAll(MethodCallExpr.class);
                for (int j = 0; j < methodCallExprs.size(); j++) {
                    MethodCallExpr mce = methodCallExprs.get(j);
                    if (mce.getName().toString().equals(query.getMethod())
                            && mce.getArguments().size() == query.getArguments().size()) {
                        isMethodMatch.set(index, true);
                        // System.out.println("MCE: " + mce);
                        try {
                            ResolvedMethodDeclaration resolvedMethodDeclaration = mce.resolve();
                            String fullyQualifiedName = resolvedMethodDeclaration.getPackageName() + "."
                                    + resolvedMethodDeclaration.getClassName();
                            isResolved.set(index, true);
                            boolean isArgumentTypeMatch = true;
                            for (int k = 0; k < resolvedMethodDeclaration.getNumberOfParams(); k++) {
                                if (!query.getArguments().get(k)
                                        .equals(resolvedMethodDeclaration.getParam(k).describeType())) {
                                    isArgumentTypeMatch = false;
                                    break;
                                }
                            }
                            if (isArgumentTypeMatch
                                    && fullyQualifiedName.equals(queries.get(index).getFullyQualifiedName())) {
                                isResolvedAndParameterMatch.set(index, true);
                                lines.add(mce.getBegin().get().line);
                                break;
                            }
                        } catch (UnsolvedSymbolException unsolvedSymbolException) {
                            // isResolved.set(index, false);
                        }
                    }
                }
            }


            boolean isSuccess = true;
            
            for (int i = 0; i < queries.size(); i++) {
                System.out.println("Query " + (i + 1) + ": " + queries.get(i));
                if (isMethodMatch.get(i)) {
                    if (isResolved.get(i)) {
                        if (isResolvedAndParameterMatch.get(i)) {
                            System.out.println("Resolved and match argument type");
                        } else {
                            isSuccess = false;
                            System.out.println("Resolved but argument type doesn't match :" + queries.get(i).getArguments());
                        }
                    } else {
                        isSuccess = false;
                        System.out.println("Can't resolve :" + queries.get(i).getMethod());
                    }
                } else {
                    isSuccess = false;
                    System.out.println("No method match :" + queries.get(i).getMethod());
                }
            }

            if (isSuccess) {
                resolvedFile.setPathFile(pathFile);
                resolvedFile.setLines(lines);
                resolvedFile.setCodes(getSnippetCode(pathFile, lines));
                System.out.println("=== SUCCESS ===");
            } else {
                System.out.println("File location: " + file.toString());
            }

        } catch (ParseProblemException parseProblemException) {
            System.out.println("=== Parse Problem Exception in Type Resolution ===");
            System.out.println("File location: " + pathFile);
        } catch (RuntimeException runtimeException) {
            System.out.println("=== Runtime Exception in Type Resolution ===");
            System.out.println("File location: " + pathFile);
        } catch (IOException io) {
            System.out.println("=== IO Exception in Type Resolution ===");
            System.out.println("File location: " + pathFile);
        }

        return resolvedFile;

    }

    private static String prepareQuery(ArrayList<Query> queries) {
    //* formats entire queries arraylist into a space-separated string
        String queriesAsString = "";
        for (int i = 0; i < queries.size(); i++) {
            // gets FQN and method of query: "<FQN> <method> <param1>  ... <param n>"
            queriesAsString += queries.get(i).toStringRequest(); 
            if (i != (queries.size() - 1)) queriesAsString += " ";
        }

        return queriesAsString;
    }

    private static void printQuery(ArrayList<Query> queries) {
        System.out.println("============");
        System.out.println("Your Queries");
        System.out.println("============");
        for (int i = 0; i < queries.size(); i++) {
            System.out.println("Query " + (i + 1) + ": " + queries.get(i));
        }
    }

    private static ArrayList<Query> parseQueries(String s) {
    //* takes query user types in and returns an arraylist of Query objects formatted accordingly
        ArrayList<Query> queries = new ArrayList<Query>(); //* queries start out without parameters
        
        s = s.replace(" ", ""); //* s will be the string query user types in 
        while (!s.equals("")) {
            int tagLocation = s.indexOf('#');
            int leftBracketLocation = s.indexOf('(');
            int rightBracketLocation = s.indexOf(')');
            if (tagLocation == -1 | leftBracketLocation == -1 || rightBracketLocation == -1
            && tagLocation < leftBracketLocation && leftBracketLocation < rightBracketLocation) {
                System.out.println("Your query isn't accepted");
                System.out.println("Query Format: " + "method(argument_1, argument_2, ... , argument_n)");
                System.out.println("Example: "
                        + "android.app.Notification.Builder#addAction(int, java.lang.CharSequence, android.app.PendingIntent)");
                        ;
                        return new ArrayList<Query>();
            } else {
                String fullyQualifiedName = s.substring(0, tagLocation);
                String method = s.substring(tagLocation + 1, leftBracketLocation); // subtring takes start and end-1 index
                String args = s.substring(leftBracketLocation + 1, rightBracketLocation);
                ArrayList<String> arguments = new ArrayList<String>();
                if (!args.equals("")) { // handle if no arguments
                    String[] arr = args.split(",");
                    for (int i = 0; i < arr.length; i++) {
                        arguments.add(arr[i]);
                    }
                }
                //* basically instantiates a query object with the FQN, method and args of the query user typed in
                Query query = new Query();
                query.setFullyQualifiedName(fullyQualifiedName);
                query.setMethod(method);
                query.setArguments(arguments);
                queries.add(query);
                int andLocation = s.indexOf('&');
                if (andLocation == -1) {
                    s = "";
                } else {
                    s = s.substring(andLocation + 1);
                }
            }
        }

        if (prepareQuery(queries).length() > 128) {
            System.out.println("I'm sorry");
            System.out.println("Your query length can't more than 128");
            System.out.println("Your query length are: " + prepareQuery(queries).length());
            System.out.println("This is github search rule, I can't do anything to tackle this problem");
            return new ArrayList<Query>();
        }

        return queries;
    }

    private static void initUniqueFolderToSaveData(ArrayList<Query> queries) {

        String folderName = "";
        for (int i = 0; i < queries.size(); i++) {
            folderName += queries.get(i).getMethod();
            if (i != queries.size() - 1) {
                folderName += "&";
            }
        }

        File dataFolder = new File(DATA_LOCATION);
        if (!dataFolder.exists()) {
            dataFolder.mkdir();
        }

        DATA_LOCATION = DATA_LOCATION + folderName + "/";

        File exactFolder = new File(DATA_LOCATION);
        if (!exactFolder.exists()) {
            exactFolder.mkdir();
        }

        File files = new File(DATA_LOCATION + "files/");
        if (!files.exists()) {
            files.mkdir();
        }

        File jarFolder = new File(JARS_LOCATION);
        if (!jarFolder.exists()) {
            jarFolder.mkdir();
        }

    }

    /**
     * Convert github html url to download url input:
     * https://github.com/shuchen007/ThinkInJavaMaven/blob/85b402a81fc0b3f2f039b34c23be416322ef14b4/src/main/java/sikaiClient/IScyxKtService.java
     * output:
     * https://raw.githubusercontent.com/shuchen007/ThinkInJavaMaven/85b402a81fc0b3f2f039b34c23be416322ef14b4/src/main/java/sikaiClient/IScyxKtService.java
     */
    private static String convertHTMLUrlToDownloadUrl(String html_url) {
        String[] parts = html_url.split("/");
        String download_url = "https://raw.githubusercontent.com/";
        int l = parts.length;
        for (int i = 0; i < l; i++) {
            if (i >= 3) {
                if (i != 5) {
                    if (i != l - 1) {
                        download_url = download_url.concat(parts[i] + '/');
                    } else {
                        download_url = download_url.concat(parts[i]);
                    }
                }
            }
        }

        return download_url;
    }

    public static class URLRunnable implements Runnable {
        private final String url;

        URLRunnable(String query, int lower_bound, int upper_bound, int page, int per_page_limit) {
            upper_bound++;
            lower_bound--;
            String size = lower_bound + ".." + upper_bound; // lower_bound < size < upper_bound
            this.url = endpoint + "?" + PARAM_QUERY + "=" + query + "+in:file+language:java"
                    + "&" + PARAM_PAGE + "=" + page + "&" + PARAM_PER_PAGE + "=" + per_page_limit;
        }

        @Override
        public void run() {
            Response response = handleGithubRequestWithUrl(url);
            JSONArray item = response.getItem();
            // System.out.println("Request: " + response.getUrlRequest());
            // System.out.println("Number items: " + item.length());
            synchronizedData.addArray(item);
        }
    }

    private static Response handleCustomGithubRequest(String query, int lower_bound, int upper_bound, int page,
            int per_page_limit) {
        // The size range is exclusive
        upper_bound++;
        lower_bound--;
        String size = lower_bound + ".." + upper_bound; // lower_bound < size < upper_bound

        String url;
        Response response = new Response();

        url = endpoint + "?" + PARAM_QUERY + "=" + query + "+in:file+language:java" + "&"
                + PARAM_PAGE + "=" + page + "&" + PARAM_PER_PAGE + "=" + per_page_limit;
        //* handleGithubRequestWithURL returns response object obtained from querying GH API
        response = handleGithubRequestWithUrl(url);

        return response;
    }

    private static Response handleGithubRequestWithUrl(String url) {

        boolean response_ok = false;
        Response response = new Response();
        int responseCode;

        // encode the space into %20
        url = url.replace(" ", "%20");
        GithubToken token = synchronizedFeeder.getAvailableGithubToken();

        do {
            HttpRequest request = HttpRequest.get(url, false).authorization("token " + token.getToken());
            System.out.println();
            System.out.println("Request: " + request);
            // System.out.println("Token: " + token);
            // System.out.println("Thread: " + Thread.currentThread().toString());

            // handle response
            responseCode = request.code();
            if (responseCode == RESPONSE_OK) {
                // System.out.println("Header: " + request.headers());
                response.setCode(responseCode);
                JSONObject body = new JSONObject(request.body());
                response.setTotalCount(body.getInt("total_count"));
                if (body.getInt("total_count") > 0) {
                    response.setItem(body.getJSONArray("items")); //* response's Iten is "items" returned by GH query!
                    response.setUrlRequest(request.toString());
                    response.setNextUrlRequest(getNextLinkFromResponse(request.header("Link")));
                }
                response_ok = true;
            } else if (responseCode == BAD_CREDENTIAL) {
                System.out.println("Authorization problem");
                System.out.println("Please read the readme file!");
                System.out.println("Github message: " + (new JSONObject(request.body())).getString("message"));
                System.exit(-1);
            } else if (responseCode == ABUSE_RATE_LIMITS) {
                System.out.println("Abuse Rate Limits");
                // retry current progress after wait for a minute
                String retryAfter = request.header("Retry-After");
                try {
                    int sleepTime = 0; // wait for a while
                    if (retryAfter.isEmpty()) {
                        sleepTime = 1;
                    } else {
                        sleepTime = new Integer(retryAfter).intValue();
                    }
                    System.out.println("Retry-After: " + sleepTime);
                    TimeUnit.SECONDS.sleep(sleepTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else if (responseCode == UNPROCESSABLE_ENTITY) {
                System.out.println("Response Code: " + responseCode);
                System.out.println("Unprocessable Entity: only the first 1000 search results are available");
                System.out.println("See the documentation here: https://developer.github.com/v3/search/");
            } else {
                System.out.println("Response Code: " + responseCode);
                System.out.println("Response Body: " + request.body());
                System.out.println("Response Headers: " + request.headers());
                System.exit(-1);
            }

        } while (!response_ok && responseCode != UNPROCESSABLE_ENTITY);

        synchronizedFeeder.releaseToken(token);

        return response;
    }

    private static String getNextLinkFromResponse(String linkHeader) {

        String next = null;

        if (linkHeader != null) {
            String[] links = linkHeader.split(DELIM_LINKS);
            for (String link : links) {
                String[] segments = link.split(DELIM_LINK_PARAM);
                if (segments.length < 2)
                    continue;

                String linkPart = segments[0].trim();
                if (!linkPart.startsWith("<") || !linkPart.endsWith(">")) //$NON-NLS-1$ //$NON-NLS-2$
                    continue;
                linkPart = linkPart.substring(1, linkPart.length() - 1);

                for (int i = 1; i < segments.length; i++) {
                    String[] rel = segments[i].trim().split("="); //$NON-NLS-1$
                    if (rel.length < 2 || !META_REL.equals(rel[0]))
                        continue;

                    String relValue = rel[1];
                    if (relValue.startsWith("\"") && relValue.endsWith("\"")) //$NON-NLS-1$ //$NON-NLS-2$
                        relValue = relValue.substring(1, relValue.length() - 1);

                    if (META_NEXT.equals(relValue))
                        next = linkPart;
                }
            }
        }
        return next;
    }


    private static List<String> getNeededJars(File file) {
        List<String> jarsPath = new ArrayList<String>();
        TypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver(false),
                new JavaParserTypeSolver(new File("src/main/java")));
        StaticJavaParser.getConfiguration().setSymbolResolver(new JavaSymbolSolver(typeSolver));

        // list of specific package imported
        List<String> importedPackages = new ArrayList<String>();
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            cu.findAll(Name.class).forEach(mce -> {
                String[] names = mce.toString().split("[.]");
                if (names.length >= 2) { // filter some wrong detected import like Override, SupressWarning
                    if (importedPackages.isEmpty()) {
                        importedPackages.add(mce.toString());
                    } else {
                        boolean isAlreadyDefined = false;
                        for (int i = 0; i < importedPackages.size(); i++) {
                            if (importedPackages.get(i).contains(mce.toString())) {
                                isAlreadyDefined = true;
                                break;
                            }
                        }
                        if (!isAlreadyDefined) {
                            importedPackages.add(mce.toString());
                        }
                    }
                }
            });
        } catch (FileNotFoundException e) {
            System.out.println("EXCEPTION");
            System.out.println("File not found!");
        } catch (ParseProblemException parseException) {
            return jarsPath;
        }

        // System.out.println();
        // System.out.println("=== Imported Packages ==");
        // for (int i = 0; i < importedPackages.size(); i++) {
        // System.out.println(importedPackages.get(i));
        // }

        // filter importedPackages
        // remove the project package and java predefined package
        List<String> neededPackages = new ArrayList<String>();
        if (importedPackages.size() > 0) {
            String qualifiedName = importedPackages.get(0);
            String[] names = qualifiedName.split("[.]");
            String projectPackage = names[0].toString();
            for (int i = 1; i < importedPackages.size(); i++) { // the first package is skipped
                qualifiedName = importedPackages.get(i);
                names = qualifiedName.split("[.]");
                String basePackage = names[0];
                if (!basePackage.equals(projectPackage) && !basePackage.equals("java") && !basePackage.equals("javax")
                        && !basePackage.equals("Override")) {
                    neededPackages.add(importedPackages.get(i));
                }
            }
        }

        // System.out.println();
        // System.out.println("=== Needed Packages ==");
        // for (int i = 0; i < neededPackages.size(); i++) {
        // System.out.println(neededPackages.get(i));
        // }

        List<MavenPackage> mavenPackages = new ArrayList<MavenPackage>();

        // get the groupId and artifactId from the package qualified name
        for (int i = 0; i < neededPackages.size(); i++) {
            String qualifiedName = neededPackages.get(i);
            MavenPackage mavenPackage = getMavenPackageArtifact(qualifiedName);

            if (!mavenPackage.getId().equals("")) { // handle if the maven package is not exist
                // filter if the package is used before
                boolean isAlreadyUsed = false;
                for (int j = 0; j < mavenPackages.size(); j++) {
                    MavenPackage usedPackage = mavenPackages.get(j);
                    if (mavenPackage.getGroupId().equals(usedPackage.getGroupId())
                            && mavenPackage.getArtifactId().equals(usedPackage.getArtifactId())) {
                        isAlreadyUsed = true;
                    }
                }
                if (!isAlreadyUsed) {
                    mavenPackages.add(mavenPackage);
                }
            }
        }

        // System.out.println();
        // System.out.println("=== Maven Packages ==");
        // for (int i = 0; i < mavenPackages.size(); i++) {
        // System.out.println("GroupID: " + mavenPackages.get(i).getGroupId() + " -
        // ArtifactID: "
        // + mavenPackages.get(i).getArtifactId());
        // }

        // System.out.println();
        // System.out.println("=== Downloading Packages ==");
        for (int i = 0; i < mavenPackages.size(); i++) {
            String pathToJar = downloadMavenJar(mavenPackages.get(i).getGroupId(),
                    mavenPackages.get(i).getArtifactId());
            if (!pathToJar.equals("")) {
                // System.out.println("Downloaded: " + pathToJar);
                jarsPath.add(pathToJar);
            }
        }

        return jarsPath;
    }

    // download the latest package by groupId and artifactId
    private static String downloadMavenJar(String groupId, String artifactId) {
        String path = JARS_LOCATION + artifactId + "-latest.jar";
        String url = "http://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=" + groupId
                + "&a=" + artifactId + "&v=LATEST";
        // System.out.println("URL: " + url);
        File jarFile = new File(path);

        if (!jarFile.exists()) {
            // Equivalent command conversion for Java execution
            String[] command = { "curl", "-L", url, "-o", path };

            ProcessBuilder process = new ProcessBuilder(command);
            Process p;
            try {
                p = process.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                StringBuilder builder = new StringBuilder();
                String line = null;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                    builder.append(System.getProperty("line.separator"));
                }
                String result = builder.toString();
                System.out.print(result);

            } catch (IOException e) {
                System.out.print("error");
                e.printStackTrace();
            }
        }

        return path;

    }

    private static MavenPackage getMavenPackageArtifact(String qualifiedName) {

        MavenPackage mavenPackageName = new MavenPackage();

        String url = "https://search.maven.org/solrsearch/select?q=fc:" + qualifiedName + "&wt=json";

        HttpRequest request = HttpRequest.get(url, false);

        // handle response
        int responseCode = request.code();
        if (responseCode == RESPONSE_OK) {
            JSONObject body = new JSONObject(request.body());
            JSONObject response = body.getJSONObject("response");
            int numFound = response.getInt("numFound");
            JSONArray mavenPackages = response.getJSONArray("docs");
            if (numFound > 0) {
                mavenPackageName.setId(mavenPackages.getJSONObject(0).getString("id")); // set the id
                mavenPackageName.setGroupId(mavenPackages.getJSONObject(0).getString("g")); // set the first group id
                mavenPackageName.setArtifactId(mavenPackages.getJSONObject(0).getString("a")); // set the first artifact
                                                                                               // id
                mavenPackageName.setVersion(mavenPackages.getJSONObject(0).getString("v")); // set the first version id
            }
        } else {
            System.out.println("Response Code: " + responseCode);
            System.out.println("Response Body: " + request.body());
            System.out.println("Response Headers: " + request.headers());
        }

        return mavenPackageName;
    }

}
