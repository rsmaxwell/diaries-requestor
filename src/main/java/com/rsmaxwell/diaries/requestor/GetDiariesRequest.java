package com.rsmaxwell.diaries.requestor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import com.rsmaxwell.diaries.request.model.Diary;
import com.rsmaxwell.diaries.request.state.State;
import com.rsmaxwell.mqtt.rpc.common.Request;
import com.rsmaxwell.mqtt.rpc.common.Response;
import com.rsmaxwell.mqtt.rpc.common.Status;
import com.rsmaxwell.mqtt.rpc.requestor.RemoteProcedureCall;
import com.rsmaxwell.mqtt.rpc.requestor.Token;

public class GetDiariesRequest {

	private static final Logger log = LogManager.getLogger(GetDiariesRequest.class);

	static final int qos = 0;
	static final String clientID = "requester";
	static final String requestTopic = "request";

	static private ObjectMapper mapper = new ObjectMapper();

	static Option createOption(String shortName, String longName, String argName, String description, boolean required) {
		return Option.builder(shortName).longOpt(longName).argName(argName).desc(description).hasArg().required(required).build();
	}

	public static void main(String[] args) throws Exception {

		State state = State.read();
		log.info(String.format("state:\n%s", state.toJson()));

		Option configOption = createOption("c", "config", "Configuration", "Configuration", true);

		// @formatter:off
		Options options = new Options();
		options.addOption(configOption);
		// @formatter:on

		CommandLineParser commandLineParser = new DefaultParser();
		CommandLine commandLine = commandLineParser.parse(options, args);

		String filename = commandLine.getOptionValue("config");
		Config config = Config.read(filename);
		MqttConfig mqtt = config.getMqtt();
		String server = mqtt.getServer();
		User user = mqtt.getUser();

		MqttClientPersistence persistence = new MqttDefaultFilePersistence();
		MqttAsyncClient client = new MqttAsyncClient(server, clientID, persistence);
		MqttConnectionOptions connOpts = new MqttConnectionOptions();
		connOpts.setUserName(user.getUsername());
		connOpts.setPassword(user.getPassword().getBytes());

		// Make an RPC instance
		RemoteProcedureCall rpc = new RemoteProcedureCall(client, String.format("response/%s", clientID));

		// Connect
		log.debug(String.format("Connecting to broker: %s as '%s'", server, clientID));
		client.connect(connOpts).waitForCompletion();
		log.debug(String.format("Client %s connected", clientID));

		// Subscribe to the responseTopic
		rpc.subscribeToResponseTopic();

		List<Diary> diaries = new ArrayList<Diary>();

		// Make a request
		Request request = new Request("getDiaries");
		request.put("accessToken", state.getAccessToken());

		// Send the request as a json string
		byte[] bytes = mapper.writeValueAsBytes(request);
		Token token = rpc.request(requestTopic, bytes);

		// Wait for the response to arrive
		Response response = token.waitForResponse();
		Status status = response.getStatus();

		// Handle the response
		if (response.isOk()) {
			Object result = response.getPayload();
			if (!(result instanceof List<?>)) {
				throw new Exception(String.format("Unexpected type: %s", result.getClass().getSimpleName()));
			}

			ArrayList<?> list = (ArrayList<?>) result;
			for (Object item : list) {

				if (!(item instanceof Map)) {
					throw new Exception(String.format("Unexpected type: %s", item.getClass().getSimpleName()));
				}
				Map<?, ?> map = (Map<?, ?>) item;
				Diary d = new Diary(map);
				diaries.add(d);
			}

			String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(diaries);

			log.info(String.format("List of Diaries:\n%s", json));
		} else {
			log.info(String.format("status: %s", status.toString()));
		}

		// Disconnect
		client.disconnect().waitForCompletion();
		log.debug(String.format("Client %s disconnected", clientID));
		log.debug("exiting");
	}
}
