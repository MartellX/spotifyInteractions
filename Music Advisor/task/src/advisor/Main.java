package advisor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.*;

import javax.sound.midi.SysexMessage;
import java.net.http.*;
import java.net.URI;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;

class SpotifyAPI {
    private String ACCESS_POINT = "https://accounts.spotify.com";
    private String RESOURCE_POINT = "https://api.spotify.com";
    private String access_token = "";
    private String code = "";
    private Map<String, String> categories;
    HttpClient client = null;
    public boolean auth = false;


    SpotifyAPI(){
        client = HttpClient.newBuilder().build();
        categories = new HashMap<>();
    }

    public HttpRequest requestResource (String path){
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .header("Authorization", "Bearer " + access_token)
                .uri(URI.create(RESOURCE_POINT + path))
                .GET()
                .build();

        return httpRequest;
    }

    public List<String> newReleases() throws IOException, InterruptedException {
        List<String> outputs = new LinkedList<>();

        HttpRequest request = requestResource("/v1/browse/new-releases");
        HttpResponse response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject jsonResponse = JsonParser.parseString(response.body().toString()).getAsJsonObject();
        JsonArray albums = jsonResponse.get("albums").getAsJsonObject().get("items").getAsJsonArray();

        for (JsonElement item : albums){
            String itemOfOutput = "";
            JsonObject release = item.getAsJsonObject();
            itemOfOutput += (release.get("name").getAsString() + '\n');
            itemOfOutput += ("[");
            JsonArray artists = release.get("artists").getAsJsonArray();
            int i = 0;
            for (var artist : artists){
                i++;
                String name = artist.getAsJsonObject().get("name").getAsString();
                itemOfOutput += (name);
                if (i < artists.size()){
                    itemOfOutput += (", ");
                }
            }
            itemOfOutput += ("]\n");
            itemOfOutput += (release.get("external_urls").getAsJsonObject().get("spotify").getAsString()) + '\n';
            outputs.add(itemOfOutput);
        }

        return outputs;
    }

    public List<String> featured() throws IOException, InterruptedException {
        List<String> output = new LinkedList<>();
        HttpRequest request = requestResource("/v1/browse/featured-playlists");
        HttpResponse response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject jsonResponse = JsonParser.parseString(response.body().toString()).getAsJsonObject();
        JsonArray playlists = jsonResponse.get("playlists").getAsJsonObject().get("items").getAsJsonArray();

        for (var playlist : playlists){
            String itemOfOutput = "";
            itemOfOutput += (playlist.getAsJsonObject().get("name").getAsString() + "\n");
            itemOfOutput += (playlist.getAsJsonObject().get("external_urls")
                    .getAsJsonObject().get("spotify").getAsString());

            output.add(itemOfOutput);
        }

        return output;
    }

    public List<String> categories() throws IOException, InterruptedException {
        List<String> output = new LinkedList<>();

        HttpRequest request = requestResource("/v1/browse/categories");
        HttpResponse response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject jsonResponse = JsonParser.parseString(response.body().toString()).getAsJsonObject();
        JsonArray categories = jsonResponse.get("categories").getAsJsonObject().get("items").getAsJsonArray();


        for (var category : categories){
            output.add(category.getAsJsonObject().get("name").getAsString());
            this.categories.put(category.getAsJsonObject().get("name").getAsString(),
                    category.getAsJsonObject().get("id").getAsString());
        }

        return output;
    }

    public List<String> playlists(String C_NAME) throws IOException, InterruptedException {

        List<String> output = new LinkedList<>();

        if (categories.isEmpty()){
            HttpRequest request = requestResource("/v1/browse/categories");
            HttpResponse response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject jsonResponse = JsonParser.parseString(response.body().toString()).getAsJsonObject();
            JsonArray categories = jsonResponse.get("categories").getAsJsonObject().get("items").getAsJsonArray();
            for (var category : categories){
                this.categories.put(category.getAsJsonObject().get("name").getAsString(),
                        category.getAsJsonObject().get("id").getAsString());
            }
        }

        if (!categories.containsKey(C_NAME)){
            output.add("Unknown category name.");
            return output;
        }

        String cid = categories.get(C_NAME);
        HttpRequest request = requestResource("/v1/browse/categories/"+cid+"/playlists");
        HttpResponse response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject jsonResponse = JsonParser.parseString(response.body().toString()).getAsJsonObject();
        if(response.body().toString().contains("error")){
            output.add(jsonResponse.get("error").getAsJsonObject().get("message").getAsString());
            return output;
        }
        JsonArray playlists = jsonResponse.getAsJsonObject("playlists").getAsJsonArray("items");
        for(var playlist : playlists){
            String itemOfOutput = "";
            itemOfOutput += (playlist.getAsJsonObject().get("name").getAsString() + '\n');
            itemOfOutput += (playlist.getAsJsonObject().get("external_urls")
                    .getAsJsonObject().get("spotify").getAsString());
            output.add(itemOfOutput);
        }

        return output;
    }



    public boolean Authorization() throws IOException, InterruptedException {
        //configuring the server
        HttpServer server = HttpServer.create();
        server.bind(new InetSocketAddress(8080), 0);
        server.createContext("/",
                new HttpHandler() {
                    boolean got = false;
                    public void handle(HttpExchange exchange) throws IOException {
                        String notFound = "Not found authorization code. Try again.";
                        String succeed = "Got the code. Return back to your program.";
                        String query = exchange.getRequestURI().getQuery();
                        if(query == null) query = "";
                        String response = "";
                        if (query.contains("code")){
                            got = true;
                            code = query;
                        }
                        if (got){
                            response = succeed;
                        } else{
                            response = notFound;
                        }
                        exchange.sendResponseHeaders(200, response.length());
                        exchange.getResponseBody().write(response.getBytes());
                        exchange.getResponseBody().close();
                    }
                }
        );

        //starting server and listening for the code
        server.start();
        System.out.println("use this link to request the access code:");
        System.out.println(ACCESS_POINT + "/authorize?client_id=d7a1319e65bf4295a0623eb3d97ef685&redirect_uri=http://localhost:8080&response_type=code");
        System.out.println("waiting for code...");
        while(code.equals("")){
            Thread.sleep(1000);

        };
        System.out.println("code received");
        //code received, shut down server
        server.stop(0);


        HttpRequest request = HttpRequest.newBuilder()
                .header("Content-Type", "application/x-www-form-urlencoded")
                .uri(URI.create(ACCESS_POINT + "/api/token"))
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=authorization_code&" + code + "&redirect_uri=http://localhost:8080&client_id=d7a1319e65bf4295a0623eb3d97ef685&client_secret=ee361288c66243acbd7ee085811f5a5a"))
                .build();
        System.out.println("making http request for access_token...");
        HttpResponse response = client.send(request, HttpResponse.BodyHandlers.ofString());

        JsonObject jsonResponse = JsonParser.parseString(response.body().toString()).getAsJsonObject();
        access_token = jsonResponse.get("access_token").getAsString();
        auth = true;

        System.out.println("---SUCCESS---");
        return true;
    }

    public String getACCESS_POINT() {
        return ACCESS_POINT;
    }

    public void setACCESS_POINT(String ACCESS_POINT) {
        this.ACCESS_POINT = ACCESS_POINT;
    }

    public String getRESOURCE_POINT() {
        return RESOURCE_POINT;
    }

    public void setRESOURCE_POINT(String RESOURCE_POINT) {
        this.RESOURCE_POINT = RESOURCE_POINT;
    }
}

class View {
    private int maxPages = 5;
    private List<String> tempOutput;
    private int cursor = 0;

    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    public void viewNextOutput(){
        if (cursor >= tempOutput.size()){
            System.out.println("No more pages.");
            return;
        }
        for(var out : tempOutput.subList(cursor, cursor + maxPages)){
            System.out.println(out);
            cursor++;
        }
        System.out.println("---PAGE " + cursor / maxPages + " OF " + tempOutput.size() / maxPages + "---");
    }

    public void viewPrevOutput(){
        if (cursor - maxPages <= 0){
            System.out.println("No more pages.");
            return;
        }

        for(var out : tempOutput.subList(cursor - maxPages*2, cursor - maxPages)){
            System.out.println(out);
            cursor--;
        }

        System.out.println("---PAGE " + cursor / maxPages + " OF " + tempOutput.size() / maxPages + "---");
    }

    public void setTempOutput(List<String> tempOutput) {
        cursor = 0;
        this.tempOutput = tempOutput;
        viewNextOutput();
    }

    public void simpleOutput(String string){
        System.out.println(string);
    }
}

class Controller {
    View view;
    SpotifyAPI spotifyAPI;

    public Controller(List<String> argsList){
        view = new View();
        spotifyAPI = new SpotifyAPI();

        if (argsList.contains("-access")){
            int index;
            if ((index = (argsList.indexOf("-access") + 1)) != argsList.size()){
                if (!argsList.get(index).startsWith("-")){
                    spotifyAPI.setACCESS_POINT(argsList.get(index));
                }
            }
        }

        if (argsList.contains("-resource")){
            int index;
            if ((index = (argsList.indexOf("-resource") + 1)) != argsList.size()){
                if (!argsList.get(index).startsWith("-")){
                    spotifyAPI.setRESOURCE_POINT(argsList.get(index));
                }
            }
        }

        if (argsList.contains("-page")){
            int index;
            if ((index = (argsList.indexOf("-page") + 1)) != argsList.size()){
                if (!argsList.get(index).startsWith("-")){
                    view.setMaxPages(Integer.parseInt(argsList.get(index)));
                }
            }
        }
    }

    public void sendOutput (List<String> outputs){
        view.setTempOutput(outputs);
    }

    public void sendOutput (String outputs){
        view.simpleOutput(outputs);
    }

    public void handleInput() throws IOException, InterruptedException {
        String input;
        Scanner sc = new Scanner(System.in);
        while(!(input = sc.nextLine()).equals("exit")){
                switch (input.split(" ")[0]) {
                    case "new":
                        if (!spotifyAPI.auth){
                            sendOutput("Please, provide access for application.");
                            break;
                        }
                        sendOutput("---NEW RELEASES---");
                        sendOutput(spotifyAPI.newReleases());
                        break;
                    case "featured":
                        if (!spotifyAPI.auth){
                            sendOutput("Please, provide access for application.");
                            break;
                        }
                        sendOutput("---FEATURED---");
                        sendOutput(spotifyAPI.featured());
                        break;
                    case "categories":
                        if (!spotifyAPI.auth){
                            sendOutput("Please, provide access for application.");
                            break;
                        }
                        sendOutput("---CATEGORIES---");
                        sendOutput(spotifyAPI.categories());
                        break;
                    case "playlists":
                        if (!spotifyAPI.auth){
                            sendOutput("Please, provide access for application.");
                            break;
                        }
                        if (input.split(" ").length > 1) {
                            List<String> temp = spotifyAPI.playlists(input.substring(input.indexOf(" ") + 1));
                            if (temp.size() == 1) {
                                sendOutput(temp.get(0));
                            }
                            else{
                                sendOutput(temp);
                            }
                        }
                        break;
                    case "auth":
                        spotifyAPI.Authorization();
                        break;
                    case "next":
                        view.viewNextOutput();
                        break;
                    case "prev":
                        view.viewPrevOutput();
                        break;
                }
        }

        sendOutput("---GOODBYE!---");
    }
}

public class Main {


    public static void main(String[] args) throws IOException, InterruptedException {
        List<String> argsList = List.of(args);
        Controller controller = new Controller(argsList);
        controller.handleInput();


    }
}