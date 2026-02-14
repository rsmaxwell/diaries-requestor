package com.rsmaxwell.diaries.requestor;

import java.util.ArrayList;
import java.util.List;

import com.rsmaxwell.mqtt.rpc.common.buildinfo.IBuildInfo;

public class BuildInfoTest {

	public static void main(String[] args) throws Exception {

		List<IBuildInfo> infos = new ArrayList<IBuildInfo>();
		infos.add(new com.rsmaxwell.mqtt.rpc.common.buildinfo.BuildInfo());
		infos.add(new com.rsmaxwell.mqtt.rpc.requestor.buildinfo.BuildInfo());

		for (IBuildInfo info : infos) {
			info.printAll();
		}
	}

}
