package rally.lego.cc;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rallydev.rest.RallyRestApi;
import com.rallydev.rest.request.QueryRequest;
import com.rallydev.rest.request.UpdateRequest;
import com.rallydev.rest.response.QueryResponse;
import com.rallydev.rest.response.UpdateResponse;
import com.rallydev.rest.util.Fetch;

public class CostCenter {

	private RallyRestApi api = null;

	public CostCenter() {

	}

	/**
	 * 
	 * @param args
	 */
	public static void main(String[] args) {

		Map<String, String> argsMap = new HashMap<String, String>();

		if (args == null || args.length < 6) {
			printUsage();
			return;
		}
		for (int index = 0; index < args.length; index++) {
			String argument = args[index];
			if (argument.startsWith("-")) {
				argsMap.put(argument, args[++index]);
			}

		}

		String subAdminPassword = argsMap.get("-p");
		String subAdminUsername = argsMap.get("-u");
		String csvFile = argsMap.get("-f");

		System.out.println("args: " + argsMap);

		CostCenter cs = new CostCenter();
		try {
			cs.api = new RallyRestApi(new URI("https://rally1.rallydev.com"),
					subAdminUsername, subAdminPassword);
			Set<User> refs2Update = cs.filterCSVRecords(csvFile);
			cs.updateCostCenters(refs2Update);
		} catch (FileNotFoundException e) {
			System.out.printf(
					"Error: File %s could not be found at the given location",
					csvFile);
			e.printStackTrace();
		} catch (URISyntaxException e) {
			System.out
					.printf("Error: Could not connect to https://rally1.rallydev.com. Please check your internet connection");
		} catch (IOException e) {
			System.out
					.printf("Error: File %s could not be read. Please check your permissions",
							csvFile);
		}

	}

	/**
	 * Figure out what users need a change in cost center
	 * 
	 * @param argsMap
	 * @return
	 * @throws URISyntaxException
	 * @throws IOException
	 */
	public Set<User> filterCSVRecords(String csvFile)
			throws URISyntaxException, IOException {

		String username;
		String costCenter;

		Map<String, String> csvUser2CostCenter = getRecordsFromCSV(csvFile);

		// read cost centers from Rally
		System.out.println("Starting extracting data from Rally...");
		QueryRequest request = new QueryRequest("user");
		request.setFetch(new Fetch("UserName", "_ref", "CostCenter"));

		QueryResponse resp = api.query(request);
		Set<User> users2Update = new HashSet<User>();

		if (resp.wasSuccessful()) {

			String ref = "";
			String csvCostCenter = "";

			for (JsonElement result : resp.getResults()) {
				JsonObject juser = result.getAsJsonObject();
				username = juser.get("UserName").getAsString();
				costCenter = juser.get("CostCenter").getAsString();
				ref = juser.get("_ref").getAsString();
				csvCostCenter = csvUser2CostCenter.get(username);
				if (csvCostCenter != null && !csvCostCenter.equals(costCenter)) {
					users2Update.add(new User(username, csvCostCenter, ref));
				}

			}
		}

		return users2Update;

	}

	@SuppressWarnings("resource")
	private Map<String, String> getRecordsFromCSV(String csvFile)
			throws FileNotFoundException, IOException {
		Map<String, String> csvUser2CostCenterMap = new HashMap<String, String>();

		BufferedReader reader = new BufferedReader(new FileReader(csvFile));
		String record = null;
		String[] recordTK = new String[2];
		String username = null;
		String costCenter = null;

		// read cost centers from the CSV
		System.out.println("Starting reading the data from CSV...");
		while ((record = reader.readLine()) != null) {
			recordTK = record.split(",");
			username = recordTK[0];
			costCenter = recordTK[1];
			if (username == null || costCenter == null) {
				throw new IOException(String.format("invalid record found: %s",
						record));
			}

			csvUser2CostCenterMap.put(username, costCenter);
		}
		System.out.println("Finished reading the data from CSV...");
		return csvUser2CostCenterMap;
	}

	/**
	 * Update all the users that need a change in CostCenter
	 * 
	 * @param users2Update
	 * @throws IOException
	 */
	private void updateCostCenters(Set<User> users2Update)
			throws IOException {
		if (users2Update == null || users2Update.isEmpty()) {
			System.out
					.println("All users have correct costcenters. No need to change...");
			return;
		}
		System.out.println("Update Records: " + users2Update);
		System.out.println("Starting updating users in Rally...");
		String costCenter = null;
		for (User user : users2Update) {
			JsonObject updateUser = new JsonObject();
			costCenter = user.getCostCenter();
			if (costCenter == null) {
				costCenter = "None";
			}
			updateUser.addProperty("CostCenter", costCenter);
			UpdateRequest updateRequest = new UpdateRequest(user.getRef(), updateUser);
			UpdateResponse updateResponse = api.update(updateRequest);
			updateUser = updateResponse.getObject();
			 System.out.println(String.format("Updated User %s New Cost Center = %s",
			 user.getUserName(), user.getCostCenter()));
		}
		System.out.println("Done updating users in Rally...");
	}

	/**
	 * Print the usage
	 */
	private static void printUsage() {
		System.out
				.println("Usage: java rally.lego.cc.CostCenter -u <Rally Sub admin username> -p <Rally Sub admin password> -f <csv file>");

	}

	/**
	 * Class to store user data
	 * 
	 * @author kkale
	 * 
	 */
	class User {

		private String userName = "";
		private String costCenter = "";
		private String ref = "";

		public User(String userName, String costCenter, String ref) {
			this.userName = userName;
			this.costCenter = costCenter;
			this.ref = ref;
		}

		public String getUserName() {
			return userName;
		}

		public void setUserName(String user) {
			this.userName = user;
		}

		public String getCostCenter() {
			return costCenter;
		}

		public void setCostCenter(String costCenter) {
			this.costCenter = costCenter;
		}

		public String getRef() {
			return ref;
		}

		public void setRef(String ref) {
			this.ref = ref;
		}

		@Override
		public String toString() {
			return "User [userName=" + userName + ", costCenter=" + costCenter
					+ "ref = " + ref + "]";
		}

	}
}
