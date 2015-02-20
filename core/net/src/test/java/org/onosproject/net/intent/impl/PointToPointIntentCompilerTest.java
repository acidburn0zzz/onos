/*
 * Copyright 2014 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.net.intent.impl;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.onosproject.TestApplicationId;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.intent.AbstractIntentTest;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentTestsMocks;
import org.onosproject.net.intent.PathIntent;
import org.onosproject.net.intent.PointToPointIntent;

import java.util.List;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.onosproject.net.DefaultEdgeLink.createEdgeLink;
import static org.onosproject.net.DeviceId.deviceId;
import static org.onosproject.net.NetTestTools.APP_ID;
import static org.onosproject.net.NetTestTools.connectPoint;
import static org.onosproject.net.PortNumber.portNumber;
import static org.onosproject.net.intent.LinksHaveEntryWithSourceDestinationPairMatcher.linksHasPath;

/**
 * Unit tests for the HostToHost intent compiler.
 */
public class PointToPointIntentCompilerTest extends AbstractIntentTest {

    private static final ApplicationId APPID = new TestApplicationId("foo");

    private TrafficSelector selector = new IntentTestsMocks.MockSelector();
    private TrafficTreatment treatment = new IntentTestsMocks.MockTreatment();

    /**
     * Creates a PointToPoint intent based on ingress and egress device Ids.
     *
     * @param ingressIdString string for id of ingress device
     * @param egressIdString  string for id of egress device
     * @return PointToPointIntent for the two devices
     */
    private PointToPointIntent makeIntent(String ingressIdString,
                                          String egressIdString) {
        return new PointToPointIntent(APPID, selector, treatment,
                                      connectPoint(ingressIdString, 1),
                                      connectPoint(egressIdString, 1));
    }

    /**
     * Creates a compiler for HostToHost intents.
     *
     * @param hops string array describing the path hops to use when compiling
     * @return HostToHost intent compiler
     */
    private PointToPointIntentCompiler makeCompiler(String[] hops) {
        PointToPointIntentCompiler compiler =
                new PointToPointIntentCompiler();
        compiler.pathService = new IntentTestsMocks.MockPathService(hops);
        return compiler;
    }


    /**
     * Tests a pair of devices in an 8 hop path, forward direction.
     */
    @Test
    public void testForwardPathCompilation() {

        PointToPointIntent intent = makeIntent("d1", "d8");

        String[] hops = {"d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8"};
        PointToPointIntentCompiler compiler = makeCompiler(hops);

        List<Intent> result = compiler.compile(intent, null, null);
        assertThat(result, is(Matchers.notNullValue()));
        assertThat(result, hasSize(1));
        Intent forwardResultIntent = result.get(0);
        assertThat(forwardResultIntent instanceof PathIntent, is(true));

        if (forwardResultIntent instanceof PathIntent) {
            PathIntent forwardPathIntent = (PathIntent) forwardResultIntent;
            // 7 links for the hops, plus one default lnk on ingress and egress
            assertThat(forwardPathIntent.path().links(), hasSize(hops.length + 1));
            assertThat(forwardPathIntent.path().links(), linksHasPath("d1", "d2"));
            assertThat(forwardPathIntent.path().links(), linksHasPath("d2", "d3"));
            assertThat(forwardPathIntent.path().links(), linksHasPath("d3", "d4"));
            assertThat(forwardPathIntent.path().links(), linksHasPath("d4", "d5"));
            assertThat(forwardPathIntent.path().links(), linksHasPath("d5", "d6"));
            assertThat(forwardPathIntent.path().links(), linksHasPath("d6", "d7"));
            assertThat(forwardPathIntent.path().links(), linksHasPath("d7", "d8"));
        }
    }

    /**
     * Tests a pair of devices in an 8 hop path, forward direction.
     */
    @Test
    public void testReversePathCompilation() {

        PointToPointIntent intent = makeIntent("d8", "d1");

        String[] hops = {"d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8"};
        PointToPointIntentCompiler compiler = makeCompiler(hops);

        List<Intent> result = compiler.compile(intent, null, null);
        assertThat(result, is(Matchers.notNullValue()));
        assertThat(result, hasSize(1));
        Intent reverseResultIntent = result.get(0);
        assertThat(reverseResultIntent instanceof PathIntent, is(true));

        if (reverseResultIntent instanceof PathIntent) {
            PathIntent reversePathIntent = (PathIntent) reverseResultIntent;
            assertThat(reversePathIntent.path().links(), hasSize(hops.length + 1));
            assertThat(reversePathIntent.path().links(), linksHasPath("d2", "d1"));
            assertThat(reversePathIntent.path().links(), linksHasPath("d3", "d2"));
            assertThat(reversePathIntent.path().links(), linksHasPath("d4", "d3"));
            assertThat(reversePathIntent.path().links(), linksHasPath("d5", "d4"));
            assertThat(reversePathIntent.path().links(), linksHasPath("d6", "d5"));
            assertThat(reversePathIntent.path().links(), linksHasPath("d7", "d6"));
            assertThat(reversePathIntent.path().links(), linksHasPath("d8", "d7"));
        }
    }

    /**
     * Tests compilation of the intent which designates two different ports on the same switch.
     */
    @Test
    public void testSameSwitchDifferentPortsIntentCompilation() {
        ConnectPoint src = new ConnectPoint(deviceId("1"), portNumber(1));
        ConnectPoint dst = new ConnectPoint(deviceId("1"), portNumber(2));
        PointToPointIntent intent = new PointToPointIntent(APP_ID, selector, treatment, src, dst);

        String[] hops = {"1"};
        PointToPointIntentCompiler sut = makeCompiler(hops);

        List<Intent> compiled = sut.compile(intent, null, null);

        assertThat(compiled, hasSize(1));
        assertThat(compiled.get(0), is(instanceOf(PathIntent.class)));
        Path path = ((PathIntent) compiled.get(0)).path();

        assertThat(path.links(), hasSize(2));
        Link firstLink = path.links().get(0);
        assertThat(firstLink, is(createEdgeLink(src, true)));
        Link secondLink = path.links().get(1);
        assertThat(secondLink, is(createEdgeLink(dst, false)));
    }
}
