package xyz.derkades.ssx_connector;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.UUID;

import org.bukkit.configuration.file.FileConfiguration;

import com.google.gson.Gson;

import xyz.derkades.ssx_connector.PlaceholderRegistry.GlobalPlaceholder;
import xyz.derkades.ssx_connector.PlaceholderRegistry.PlayerPlaceholder;

public class PlaceholderSender implements Runnable {
	
	private final Stack<String> addresses = new Stack<>();

	@Override
	public void run() {
		final FileConfiguration config = Main.instance.getConfig();
		
		// Only send request to one address every time. When requests have been
		// sent to all addresses, repopulate the stack so the cycle can start over.
		
		if (this.addresses.isEmpty()) {
			config.getStringList("addresses").forEach(this.addresses::push);
		}
		
		final String address = this.addresses.pop();
		
		final String encodedPassword = encode(config.getString("password"));
		
		// First get a list of players so we know which player placeholders to send
		final Map<UUID, String> players;
		
		try {
			players = getPlayerList(address, encodedPassword);
		} catch (final MalformedURLException e) {
			PingLogger.logFail(address, "Invalid address");
			return;
		} catch (final IOException e) {
			PingLogger.logFail(address, "IOException:" + e.getMessage());
			return;
		} catch (final PingException e) {
			PingLogger.logFail(address, e.getMessage());
			return;
		}
	
		// Collect placeholders to single map
		final Map<String, Object> placeholders = new HashMap<>();

		final Map<String, String> playerValues = new HashMap<>();
		
		PlaceholderRegistry.forEach(p -> {
			if (p instanceof PlayerPlaceholder) {
				final PlayerPlaceholder pp = (PlayerPlaceholder) p;
				final Map<String, String> map = new HashMap<>();
				players.forEach((u, n) -> playerValues.put(u.toString(), pp.getValue(u, n)));
				placeholders.put(pp.getKey(), map);
			} else {
				final GlobalPlaceholder gp = (GlobalPlaceholder) p;
				placeholders.put(gp.getKey(), gp.getValue());
			}
		});
		
		final String serverName = config.getString("server-name");
		
		try {
			sendPlaceholders(address, encodedPassword, serverName, placeholders);
		} catch (final MalformedURLException e) {
			PingLogger.logFail(address, "Invalid address");
			return;
		} catch (final IOException e) {
			PingLogger.logFail(address, "IOException:" + e.getMessage());
			return;
		} catch (final PingException e) {
			PingLogger.logFail(address, e.getMessage());
			return;
		}

		PingLogger.logSuccess(address);
	}
	
	private void sendPlaceholders(String address, final String encodedPassword, final String serverName,
			final Map<String, Object> placeholders) throws IOException, PingException {
		address = "http://" + address;
		final String json = new Gson().toJson(placeholders).toString();
		final String parameters = String.format("password=%s&server=%s&data=%s",
				encodedPassword, serverName, this.encode(json));

		final HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
		connection.setRequestMethod("POST");
		connection.setRequestProperty("Content-Length", parameters.length() + "");
		connection.setDoOutput(true);

		final DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
		outputStream.writeBytes(parameters);
		
		if (connection.getResponseCode() == 401) {
			throw new PingException("Invalid password");
		}

		if (connection.getResponseCode() == 400) {
			throw new PingException("Bad request. Make sure you are using the latest plugin version on all servers. If you are, please report this issue.");
		}
	}
	
	private Map<UUID, String> getPlayerList(String address, final String encodedPassword) throws PingException, IOException {
		address = new StringBuilder("http://").append("/players?password=").append(encodedPassword).toString();

		final HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();

		if (connection.getResponseCode() == 401) {
			throw new PingException("Invalid password");
		}

		if (connection.getResponseCode() == 400) {
			throw new PingException("Bad request. Make sure you are using the latest plugin version on all servers. If you are, please report this issue.");
		}

		final InputStream inputStream = connection.getInputStream();
		final BufferedReader streamReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
		final StringBuilder responseBuilder = new StringBuilder();

		String responseString;
		while ((responseString = streamReader.readLine()) != null) {
			responseBuilder.append(responseString);
		}
		
		inputStream.close();

		final Map<?, ?> map = new Gson().fromJson(responseBuilder.toString(), Map.class);
		final Map<UUID, String> map2 = new HashMap<>();
		map.forEach((k, v) -> map2.put(UUID.fromString(String.valueOf(k)), String.valueOf(v)));
		return map2;
	}

	private String encode(final Object object) {
		try {
			return URLEncoder.encode(object.toString(), "UTF-8");
		} catch (final UnsupportedEncodingException e) {
			throw new RuntimeException(e.getMessage());
		}
	}
	
	private static final class PingException extends Exception {
		
		private static final long serialVersionUID = 1L;

		PingException(final String message){
			super(message);
		}
		
	}

}
