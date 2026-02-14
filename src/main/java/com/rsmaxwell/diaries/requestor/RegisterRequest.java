package com.rsmaxwell.diaries.requestor;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttClientPersistence;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MqttDefaultFilePersistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rsmaxwell.diaries.common.config.Config;
import com.rsmaxwell.diaries.common.config.MqttConfig;
import com.rsmaxwell.diaries.common.config.User;
import com.rsmaxwell.mqtt.rpc.common.Request;
import com.rsmaxwell.mqtt.rpc.common.Response;
import com.rsmaxwell.mqtt.rpc.common.Status;
import com.rsmaxwell.mqtt.rpc.requestor.RemoteProcedureCall;

public class RegisterRequest {

	private static final Logger log = LogManager.getLogger(RegisterRequest.class);

	static final int qos = 0;
	static final String clientID = "requester";
	static final String requestTopic = "request";

	static private ObjectMapper mapper = new ObjectMapper();

	static Option createOption(String shortName, String longName, String argName, String description, boolean required) {
		return Option.builder(shortName).longOpt(longName).argName(argName).desc(description).hasArg().required(required).build();
	}

	public static void main(String[] args) throws Exception {
		log.info("diaries Register Request");

		Option configOption = createOption("c", "config", "Configuration", "Configuration", true);
		Option usernameOption = createOption("u", "username", "Username", "Username", true);
		Option passwordOption = createOption("p", "password", "Password", "Password", true);
		Option firstnameOption = createOption("f", "firstname", "Firstname", "First name", true);
		Option lastnameOption = createOption("l", "lastname", "Lastname", "Last name", true);
		Option knownasOption = createOption("l", "knownas", "Knownas", "Knownas", true);
		Option emailOption = createOption("l", "email", "Email", "Email", true);
		Option phoneOption = createOption("l", "phone", "Phone", "Phone", true);

		// @formatter:off
		Options options = new Options();
		options.addOption(configOption)
		       .addOption(usernameOption)
			   .addOption(passwordOption)
		       .addOption(firstnameOption)
		       .addOption(lastnameOption)
		       .addOption(knownasOption)
		       .addOption(emailOption)
		       .addOption(phoneOption);
		// @formatter:on

		CommandLineParser commandLineParser = new DefaultParser();
		CommandLine commandLine = commandLineParser.parse(options, args);

		String filename = commandLine.getOptionValue("config");
		Config config = Config.read(filename);
		MqttConfig mqtt = config.getMqtt();
		String server = mqtt.getServer();
		User user = mqtt.getUser();

		// Connect
		MqttConnectionOptions connOpts = new MqttConnectionOptions();
		connOpts.setUserName(user.getUsername());
		connOpts.setPassword(user.getPassword().getBytes());

		MqttClientPersistence persistence = new MqttDefaultFilePersistence();
		MqttAsyncClient client = new MqttAsyncClient(server, clientID, persistence);
		RemoteProcedureCall rpc = new RemoteProcedureCall(client, String.format("response/%s", clientID));

		log.debug(String.format("Connecting to broker: %s as '%s'", server, clientID));
		client.connect(connOpts).waitForCompletion();
		rpc.subscribeToResponseTopic();

		// Make a request
		Request request = new Request("register");
		request.put("username", commandLine.getOptionValue("username"));
		request.put("password", commandLine.getOptionValue("password"));
		request.put("firstname", commandLine.getOptionValue("firstname"));
		request.put("lastname", commandLine.getOptionValue("lastname"));
		request.put("knownas", commandLine.getOptionValue("knownas"));
		request.put("email", commandLine.getOptionValue("email"));
		request.put("phone", commandLine.getOptionValue("phone"));

		// Send the request as a JSON string
		byte[] bytes = mapper.writeValueAsBytes(request);
		Response response = rpc.request(requestTopic, bytes).waitForResponse();
		Status status = response.getStatus();

		// Handle the response
		if (status.isOk()) {
			Long id = (Long) response.getPayload();
			log.info(String.format("User Registered: '%s', id: %d", user.getUsername(), id));
		} else {
			log.info(String.format("status: %s", status.toString()));
		}

		// Disconnect
		client.disconnect().waitForCompletion();
		log.debug(String.format("Client %s disconnected", clientID));
		log.info("Success");
	}
}
