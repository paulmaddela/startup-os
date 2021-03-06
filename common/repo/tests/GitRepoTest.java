/*
 * Copyright 2018 The StartupOS Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.startupos.common.repo.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.startupos.common.CommonModule;
import com.google.startupos.common.FileUtils;
import com.google.startupos.common.repo.GitRepo;
import com.google.startupos.common.repo.GitRepoFactory;
import com.google.startupos.common.repo.Protos.Commit;
import com.google.startupos.common.repo.Protos.File;
import com.google.startupos.common.repo.Repo;
import dagger.Component;
import org.junit.Before;
import org.junit.Test;
import javax.inject.Singleton;
import static org.junit.Assert.assertNotEquals;

public class GitRepoTest {
  private static final String TEST_BRANCH = "test_branch";
  private static final String TEST_FILE = "test_file.txt";
  private static final String TEST_FILE_CONTENTS = "Some test file contents\n";
  private static final String COMMIT_MESSAGE = "Some commit message";

  private Repo repo;
  private GitRepo gitRepo;
  private String initialCommit;
  private String repoFolder;
  private FileUtils fileUtils;

  @Before
  public void setup() throws IOException {
    TestComponent component = DaggerGitRepoTest_TestComponent.create();
    GitRepoFactory gitRepoFactory = component.getFactory();
    fileUtils = component.getFileUtils();
    repoFolder = Files.createTempDirectory("temp").toAbsolutePath().toString();
    gitRepo = gitRepoFactory.create(repoFolder);
    gitRepo.init();
    repo = gitRepo;
    gitRepo.setFakeUsersData();
    // We need one commit to make the repo have a master branch.
    fileUtils.writeStringUnchecked(
        "initial commit", fileUtils.joinToAbsolutePath(repoFolder, "initial_commit.txt"));
    initialCommit = repo.commit(repo.getUncommittedFiles(), "Initial commit").getId();
  }

  @Singleton
  @Component(modules = CommonModule.class)
  interface TestComponent {
    GitRepoFactory getFactory();

    FileUtils getFileUtils();
  }

  @Test
  public void testThatEmptyRepoHasMasterBranch() {
    assertEquals(ImmutableList.of("master"), repo.listBranches());
  }

  @Test
  public void testAddBranch() {
    repo.switchBranch(TEST_BRANCH);
    assertEquals(ImmutableList.of("master", TEST_BRANCH), repo.listBranches());
  }

  @Test
  public void testRemoveBranch() {
    repo.switchBranch(TEST_BRANCH);
    // Switch to another branch otherwise deleting fails
    repo.switchBranch("master");
    repo.removeBranch(TEST_BRANCH);
    assertEquals(ImmutableList.of("master"), repo.listBranches());
  }

  @Test(expected = RuntimeException.class)
  public void testRemoveNonExistingBranch() {
    repo.removeBranch(TEST_BRANCH);
  }

  @Test
  public void testGetCommitIds() {
    repo.switchBranch(TEST_BRANCH);
    fileUtils.writeStringUnchecked(
        TEST_FILE_CONTENTS, fileUtils.joinToAbsolutePath(repoFolder, TEST_FILE));
    repo.commit(repo.getUncommittedFiles(), COMMIT_MESSAGE);
    assertEquals(2, gitRepo.getCommitIds(TEST_BRANCH).size());
  }

  @Test
  public void testGetCommits() {
    String initialCommitId = gitRepo.getHeadCommitId();
    repo.switchBranch(TEST_BRANCH);
    fileUtils.writeStringUnchecked(
        TEST_FILE_CONTENTS, fileUtils.joinToAbsolutePath(repoFolder, TEST_FILE));
    repo.commit(repo.getUncommittedFiles(), COMMIT_MESSAGE);
    String commitId = gitRepo.getHeadCommitId();
    assertEquals(
        ImmutableList.of(
            Commit.newBuilder().setId(initialCommitId).build(),
            Commit.newBuilder()
                .setId(commitId)
                .addFile(
                    File.newBuilder()
                        .setAction(File.Action.ADD)
                        .setCommitId(commitId)
                        .setFilename(TEST_FILE)
                        .build())
                .build()),
        repo.getCommits(TEST_BRANCH));
  }

  @Test
  public void testGetMultipleCommits() {
    String initialCommitId = gitRepo.getHeadCommitId();
    repo.switchBranch(TEST_BRANCH);
    fileUtils.writeStringUnchecked(
        TEST_FILE_CONTENTS, fileUtils.joinToAbsolutePath(repoFolder, TEST_FILE));
    repo.commit(repo.getUncommittedFiles(), COMMIT_MESSAGE);
    String commitId1 = gitRepo.getHeadCommitId();
    fileUtils.writeStringUnchecked(
        "More content", fileUtils.joinToAbsolutePath(repoFolder, TEST_FILE));
    repo.commit(repo.getUncommittedFiles(), COMMIT_MESSAGE);
    String commitId2 = gitRepo.getHeadCommitId();
    assertEquals(
        ImmutableList.of(
            Commit.newBuilder().setId(initialCommitId).build(),
            Commit.newBuilder()
                .setId(commitId1)
                .addFile(
                    File.newBuilder()
                        .setAction(File.Action.ADD)
                        .setCommitId(commitId1)
                        .setFilename(TEST_FILE)
                        .build())
                .build(),
            Commit.newBuilder()
                .setId(commitId2)
                .addFile(
                    File.newBuilder()
                        .setAction(File.Action.MODIFY)
                        .setCommitId(commitId2)
                        .setFilename(TEST_FILE)
                        .build())
                .build()),
        repo.getCommits(TEST_BRANCH));
  }

  @Test
  public void testAddFile() {
    repo.switchBranch(TEST_BRANCH);
    fileUtils.writeStringUnchecked(
        TEST_FILE_CONTENTS, fileUtils.joinToAbsolutePath(repoFolder, TEST_FILE));
    gitRepo.addFile(TEST_FILE);
    List<File> uncom = repo.getUncommittedFiles();
    assertEquals(1, repo.getUncommittedFiles().size());
  }

  @Test
  public void testAddAndCommitFile() {
    repo.switchBranch(TEST_BRANCH);
    fileUtils.writeStringUnchecked(
        TEST_FILE_CONTENTS, fileUtils.joinToAbsolutePath(repoFolder, TEST_FILE));
    gitRepo.addFile(TEST_FILE);
    repo.commit(repo.getUncommittedFiles(), COMMIT_MESSAGE);
    assertEquals(0, repo.getUncommittedFiles().size());
  }

  @Test
  public void testGetFileContents() {
    repo.switchBranch(TEST_BRANCH);
    fileUtils.writeStringUnchecked(
        TEST_FILE_CONTENTS, fileUtils.joinToAbsolutePath(repoFolder, TEST_FILE));
    gitRepo.addFile(TEST_FILE);
    repo.commit(repo.getUncommittedFiles(), COMMIT_MESSAGE);
    String commitId = gitRepo.getHeadCommitId();
    assertEquals(TEST_FILE_CONTENTS, repo.getFileContents(commitId, TEST_FILE));
  }

  @Test
  public void testGetUncommittedFilesWhenAddedFile() {
    repo.switchBranch(TEST_BRANCH);
    fileUtils.writeStringUnchecked(
        TEST_FILE_CONTENTS, fileUtils.joinToAbsolutePath(repoFolder, "added_file.txt"));
    gitRepo.addFile("added_file.txt");
    assertEquals(
        ImmutableList.of(File.newBuilder().setFilename("added_file.txt").build()),
        repo.getUncommittedFiles());
  }

  @Test
  public void testGetUncommittedFilesWhenModifiedFile() {
    repo.switchBranch(TEST_BRANCH);
    fileUtils.writeStringUnchecked(
        TEST_FILE_CONTENTS, fileUtils.joinToAbsolutePath(repoFolder, TEST_FILE));
    gitRepo.addFile(TEST_FILE);
    repo.commit(repo.getUncommittedFiles(), COMMIT_MESSAGE);
    fileUtils.writeStringUnchecked(
        "new file contents", fileUtils.joinToAbsolutePath(repoFolder, TEST_FILE));
    assertEquals(
        ImmutableList.of(
            File.newBuilder().setFilename(TEST_FILE).setAction(File.Action.MODIFY).build()),
        repo.getUncommittedFiles());
  }

  @Test
  public void testGetUncommittedFilesWhenUntrackedFile() {
    repo.switchBranch(TEST_BRANCH);
    fileUtils.writeStringUnchecked(
        TEST_FILE_CONTENTS, fileUtils.joinToAbsolutePath(repoFolder, TEST_FILE));
    assertEquals(
        ImmutableList.of(
            File.newBuilder().setFilename(TEST_FILE).setAction(File.Action.ADD).build()),
        repo.getUncommittedFiles());
  }

  @Test
  public void testGetUncommittedFilesWhenDeletedFile() {
    repo.switchBranch(TEST_BRANCH);
    fileUtils.writeStringUnchecked(
        TEST_FILE_CONTENTS, fileUtils.joinToAbsolutePath(repoFolder, TEST_FILE));
    gitRepo.addFile(TEST_FILE);
    repo.commit(repo.getUncommittedFiles(), COMMIT_MESSAGE);
    fileUtils.deleteFileOrDirectoryIfExistsUnchecked(
        fileUtils.joinToAbsolutePath(repoFolder, TEST_FILE));
    assertEquals(
        ImmutableList.of(
            File.newBuilder().setFilename(TEST_FILE).setAction(File.Action.DELETE).build()),
        repo.getUncommittedFiles());
  }

  @Test
  public void testTagHead() {
    repo.tagHead("test_tag");
    assertEquals(ImmutableList.of("test_tag"), gitRepo.getTagList());
  }

  @Test
  public void testTwoTagHead() {
    repo.tagHead("first_tag");
    repo.commit(repo.getUncommittedFiles(), COMMIT_MESSAGE);
    repo.tagHead("second_tag");
    assertEquals(ImmutableList.of("first_tag", "second_tag"), gitRepo.getTagList());
  }

  @Test
  public void testReset() {
    repo.switchBranch(TEST_BRANCH);
    fileUtils.writeStringUnchecked(
        "first commit\n", fileUtils.joinToAbsolutePath(repoFolder, TEST_FILE));
    gitRepo.addFile(TEST_FILE);
    repo.commit(repo.getUncommittedFiles(), "first commit message");
    String firstCommitId = gitRepo.getHeadCommitId();
    fileUtils.writeStringUnchecked(
        repo.getFileContents(firstCommitId, TEST_FILE) + "second commit\n",
        fileUtils.joinToAbsolutePath(repoFolder, TEST_FILE));
    gitRepo.addFile(TEST_FILE);
    repo.commit(repo.getUncommittedFiles(), "Second commit message");
    String secondCommitId = gitRepo.getHeadCommitId();
    repo.reset(firstCommitId);
    assertNotEquals(firstCommitId, secondCommitId);
    assertEquals(firstCommitId, gitRepo.getHeadCommitId());
  }

  @Test
  public void testMergeWhenAddNewFile() {
    repo.switchBranch(TEST_BRANCH);
    fileUtils.writeStringUnchecked(
        TEST_FILE_CONTENTS, fileUtils.joinToAbsolutePath(repoFolder, TEST_FILE));
    gitRepo.addFile(TEST_FILE);
    repo.commit(repo.getUncommittedFiles(), COMMIT_MESSAGE);
    repo.switchBranch("master");
    assertTrue(repo.merge(TEST_BRANCH));
    assertEquals("master", repo.currentBranch());
    assertTrue(fileUtils.fileExists(fileUtils.joinToAbsolutePath(repoFolder, TEST_FILE)));
  }

  @Test
  public void testMergeWhenFileIsChanged() {
    fileUtils.writeStringUnchecked(
        TEST_FILE_CONTENTS, fileUtils.joinToAbsolutePath(repoFolder, TEST_FILE));
    gitRepo.addFile(TEST_FILE);
    repo.commit(repo.getUncommittedFiles(), "Commit to master");
    repo.switchBranch(TEST_BRANCH);
    fileUtils.writeStringUnchecked(
        repo.getFileContents(gitRepo.getHeadCommitId(), TEST_FILE) + "New contents\n",
        fileUtils.joinToAbsolutePath(repoFolder, TEST_FILE));
    gitRepo.addFile(TEST_FILE);
    repo.commit(repo.getUncommittedFiles(), "Commit to another branch");
    repo.switchBranch("master");
    assertTrue(repo.merge(TEST_BRANCH));
    assertEquals("master", repo.currentBranch());
    assertEquals(
        TEST_FILE_CONTENTS + "New contents\n",
        repo.getFileContents(gitRepo.getHeadCommitId(), TEST_FILE));
  }

  @Test
  public void testMergeWhenFileIsDeleted() throws IOException {
    fileUtils.writeStringUnchecked(
        TEST_FILE_CONTENTS, fileUtils.joinToAbsolutePath(repoFolder, TEST_FILE));
    gitRepo.addFile(TEST_FILE);
    repo.commit(repo.getUncommittedFiles(), "Commit to master");
    repo.switchBranch(TEST_BRANCH);
    fileUtils.deleteFileOrDirectoryIfExists(TEST_FILE);
    gitRepo.addFile(TEST_FILE);
    repo.commit(repo.getUncommittedFiles(), "Commit to another branch");
    assertFalse(fileUtils.fileExists(TEST_FILE));
    repo.switchBranch("master");
    assertTrue(repo.merge(TEST_BRANCH));
    assertEquals("master", repo.currentBranch());
    assertFalse(fileUtils.fileExists(TEST_FILE));
  }

  @Test
  public void testCurrentBranch() {
    assertEquals("master", repo.currentBranch());
    repo.switchBranch(TEST_BRANCH);
    assertEquals(TEST_BRANCH, repo.currentBranch());
  }

  @Test
  public void testCommitExists() {
    assertTrue(repo.commitExists(initialCommit));
  }

  @Test
  public void testCommitExists_fakeCommit() {
    assertFalse(repo.commitExists("123245"));
  }
}

