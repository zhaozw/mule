/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.test.runner.api;

import static com.google.common.collect.Lists.newArrayList;
import static org.eclipse.aether.util.artifact.JavaScopes.COMPILE;
import static org.eclipse.aether.util.artifact.JavaScopes.PROVIDED;
import static org.eclipse.aether.util.artifact.JavaScopes.TEST;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.rules.ExpectedException.none;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.size.SmallTest;

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ArtifactDescriptorResult.class, ArtifactResult.class})
@PowerMockIgnore("javax.management.*")
@SmallTest
public class AetherClassPathClassifierTestCase extends AbstractMuleTestCase {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Rule
  public ExpectedException expectedException = none();

  private DependencyResolver dependencyResolver;
  private ClassPathClassifierContext context;
  private AetherClassPathClassifier classifier;

  private Artifact rootArtifact;
  private Dependency fooCoreDep;
  private Dependency fooToolsArtifactDep;
  private Dependency fooTestsSupportDep;
  private Dependency guavaDep;
  private Dependency derbyDriverDep;

  private List<Dependency> directDependencies;

  @Before
  public void before() throws Exception {
    this.rootArtifact = new DefaultArtifact("org.foo:foo-root:1.0-SNAPSHOT");

    this.fooCoreDep = new Dependency(new DefaultArtifact("org.foo:foo-core:1.0-SNAPSHOT"), PROVIDED);
    this.fooToolsArtifactDep = new Dependency(new DefaultArtifact("org.foo.tools:foo-artifact:1.0-SNAPSHOT"), PROVIDED);
    this.fooTestsSupportDep = new Dependency(new DefaultArtifact("org.foo.tests:foo-tests-support:1.0-SNAPSHOT"), TEST);
    this.derbyDriverDep = new Dependency(new DefaultArtifact("org.apache.derby:derby:10.11.1.1"), TEST);
    this.guavaDep = new Dependency(new DefaultArtifact("org.google:guava:18.0"), COMPILE);

    this.dependencyResolver = mock(DependencyResolver.class);
    this.context = mock(ClassPathClassifierContext.class);
    this.classifier = new AetherClassPathClassifier(dependencyResolver);

    when(context.getRootArtifact()).thenReturn(rootArtifact);
    this.directDependencies = newArrayList(fooCoreDep, fooToolsArtifactDep, fooTestsSupportDep, derbyDriverDep, guavaDep);
    when(dependencyResolver.getDirectDependencies(rootArtifact)).thenReturn(directDependencies);
  }

  @After
  public void after() throws Exception {
    verify(context, atLeastOnce()).getRootArtifact();
    verify(dependencyResolver).getDirectDependencies(rootArtifact);
  }

  @Test
  public void onlyProvidedDependenciesNoTransitiveNoManageDependenciesNoFilters() throws Exception {
    Dependency compileMuleCoreDep = fooCoreDep.setScope(COMPILE);
    Dependency compileMuleArtifactDep = fooToolsArtifactDep.setScope(COMPILE);

    ArtifactDescriptorResult defaultArtifactDescriptorResult = noManagedDependencies();

    File fooCoreArtifactFile = temporaryFolder.newFile();
    File fooToolsArtifactFile = temporaryFolder.newFile();

    when(dependencyResolver.resolveDependencies(argThat(nullValue(Dependency.class)),
                                                (List<Dependency>) argThat(hasItems(equalTo(compileMuleCoreDep),
                                                                                    equalTo(compileMuleArtifactDep))),
                                                (List<Dependency>) argThat(empty()),
                                                argThat(instanceOf(DependencyFilter.class))))
                                                    .thenReturn(newArrayList(fooCoreArtifactFile, fooToolsArtifactFile));

    ArtifactUrlClassification classification = classifier.classify(context);

    assertThat(classification.getApplicationUrls(), is(empty()));
    assertThat(classification.getPluginClassificationUrls(), is(empty()));
    assertThat(classification.getPluginSharedLibUrls(), is(empty()));
    assertThat(classification.getContainerUrls(), hasSize(2));
    assertThat(classification.getContainerUrls(),
               hasItems(fooCoreArtifactFile.toURI().toURL(), fooToolsArtifactFile.toURI().toURL()));

    verify(defaultArtifactDescriptorResult, atLeastOnce()).getManagedDependencies();
    verify(dependencyResolver, atLeastOnce()).readArtifactDescriptor(any(Artifact.class));
    verify(dependencyResolver).resolveDependencies(argThat(nullValue(Dependency.class)),
                                                   (List<Dependency>) argThat(
                                                                              hasItems(equalTo(compileMuleCoreDep),
                                                                                       equalTo(compileMuleArtifactDep))),
                                                   (List<Dependency>) argThat(empty()),
                                                   argThat(instanceOf(DependencyFilter.class)));
  }

  @Test
  public void pluginSharedLibUrlsNotDeclaredLibraryAsDirectDependency() throws Exception {
    when(context.getSharedPluginLibCoordinates()).thenReturn(newArrayList("org.foo.tools:foo-repository"));
    expectedException.expect(IllegalStateException.class);
    expectedException.expectMessage(containsString("has to be declared"));
    expectedException.expectMessage(containsString(TEST));
    classifier.classify(context);
  }

  @Test
  public void pluginSharedLibUrlsInvalidCoordiantes() throws Exception {
    when(context.getSharedPluginLibCoordinates()).thenReturn(newArrayList("foo-repository"));
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(containsString("not a valid format"));
    classifier.classify(context);
  }

  @Test
  public void pluginSharedLibUrlsNoTransitiveNoManageDependenciesNoFilters() throws Exception {
    when(context.getSharedPluginLibCoordinates())
        .thenReturn(newArrayList(derbyDriverDep.getArtifact().getGroupId() + ":" + derbyDriverDep.getArtifact().getArtifactId()));

    File derbyDriverFile = temporaryFolder.newFile();
    ArtifactResult artifactResult = mock(ArtifactResult.class);
    Artifact derbyDriverFatArtifact = derbyDriverDep.getArtifact().setFile(derbyDriverFile);
    when(artifactResult.getArtifact()).thenReturn(derbyDriverFatArtifact);
    when(dependencyResolver.resolveArtifact(argThat(equalTo(derbyDriverDep.getArtifact()))))
        .thenReturn(artifactResult);

    ArtifactDescriptorResult defaultArtifactDescriptorResult = noManagedDependencies();

    ArtifactUrlClassification classification = classifier.classify(context);

    assertThat(classification.getApplicationUrls(), is(empty()));
    assertThat(classification.getPluginClassificationUrls(), is(empty()));
    assertThat(classification.getPluginSharedLibUrls(), hasSize(1));
    assertThat(classification.getPluginSharedLibUrls(), hasItem(derbyDriverFile.toURI().toURL()));
    assertThat(classification.getContainerUrls(), is(empty()));

    verify(defaultArtifactDescriptorResult, atLeastOnce()).getManagedDependencies();
    verify(dependencyResolver, atLeastOnce()).readArtifactDescriptor(any(Artifact.class));
    verify(dependencyResolver).resolveArtifact(argThat(equalTo(derbyDriverDep.getArtifact())));
  }


  private ArtifactDescriptorResult noManagedDependencies() throws ArtifactDescriptorException {
    ArtifactDescriptorResult artifactDescriptorResult = mock(ArtifactDescriptorResult.class);
    List<Dependency> managedDependencies = Collections.<Dependency>emptyList();
    when(artifactDescriptorResult.getManagedDependencies()).thenReturn(managedDependencies);
    when(dependencyResolver.readArtifactDescriptor(any(Artifact.class))).thenReturn(artifactDescriptorResult);
    return artifactDescriptorResult;
  }

}
