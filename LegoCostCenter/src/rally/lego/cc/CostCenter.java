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
import java.util.StringTokenizer;

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
			Set<User> usersToChange = cs.filterCSVRecords(csvFile);
			cs.updateCostCenters(usersToChange);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/**
	 * Update all the users that need a change in CostCenter
	 * 
	 * @param usersToChange
	 * @throws IOException
	 */
	private void updateCostCenters(Set<User> usersToChange) throws IOException {
		if (usersToChange == null || usersToChange.isEmpty()) {
			System.out
					.println("All users have correct costcenters. No need to change...");
			return;
		}
		System.out.println("Starting updating users in Rally...");
		JsonObject updateUser = new JsonObject();
		for (User user : usersToChange) {
			updateUser.addProperty("CostCenter", user.getCostCenter());
			UpdateRequest updateRequest = new UpdateRequest(user.getRef(),
					updateUser);
			UpdateResponse updateResponse = api.update(updateRequest);
			updateUser = updateResponse.getObject();
			System.out.println(String.format(
					"Updated User %s New Cost Center = %s", user.getUserName(),
					updateUser.get("CostCenter").getAsString()));
		}
		System.out.println("Done updating users in Rally...");
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

		Set<User> csvSet = new HashSet<CostCenter.User>();

		BufferedReader reader = new BufferedReader(new FileReader(csvFile));
		String record = null;
		StringTokenizer recordTK = new StringTokenizer(",");
		String username = null;
		String costCenter = null;

		// read cost centers from the CSV
		System.out.println("Starting reading the data from CSV...");
		while ((record = reader.readLine()) != null) {
			recordTK = new StringTokenizer(record);
			if (recordTK.hasMoreTokens()) {
				username = recordTK.nextToken();
			}
			if (recordTK.hasMoreTokens()) {
				costCenter = recordTK.nextToken();
			}
			if (username == null || costCenter == null) {
				throw new IOException(String.format(
						"invalid record found: %s %s", username, costCenter));
			}

			csvSet.add(new User(username, costCenter, null));
		}
		System.out.println("Done reading the data from CSV...");

		// read cost centers from Rally
		System.out.println("Starting extracting data from Rally...");
		QueryRequest request = new QueryRequest("user");
		request.setFetch(new Fetch("UserName", "_ref", "CostCenter"));

		QueryResponse resp = api.query(request);
		User rallyUser = null;
		Set<User> rallyUpdates = new HashSet<CostCenter.User>();
		
		if (resp.wasSuccessful()) {

			String ref = "";

			for (JsonElement result : resp.getResults()) {
				JsonObject juser = result.getAsJsonObject();
				username = juser.get("UserName").getAsString();
				costCenter = juser.get("CostCenter").getAsString();
				ref = juser.get("_ref").getAsString();
				rallyUser = new User(username, costCenter, ref);
				// if the csv is not the same as the current state, add it to the update list
				if (!csvSet.contains(rallyUser)) {
					rallyUpdates.add(rallyUser);
				}
			}
		}
		System.out.println("Starting extracting data from Rally...");

		// what is left in the set at this point is the users that need a change
		// in cost center
		return rallyUpdates;
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
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result
					+ ((costCenter == null) ? 0 : costCenter.hashCode());
			result = prime * result
					+ ((userName == null) ? 0 : userName.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			User other = (User) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (costCenter == null) {
				if (other.costCenter != null)
					return false;
			} else if (!costCenter.equals(other.costCenter))
				return false;
			if (userName == null) {
				if (other.userName != null)
					return false;
			} else if (!userName.equals(other.userName))
				return false;
			return true;
		}

		private CostCenter getOuterType() {
			return CostCenter.this;
		}

	}
}
