/*
    Copyright 2018 Ericsson AB.
    For a full list of individual contributors, please see the commit history.
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/
package com.ericsson.eiffel.remrem.semantics.clone;

import java.io.File;
import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.eiffel.remrem.semantics.schemas.EiffelConstants;
import com.ericsson.eiffel.remrem.semantics.schemas.LocalRepo;
import com.ericsson.eiffel.remrem.semantics.schemas.SchemaFile;

/**
 * This class is used to Clone the eiffel repo from github
 * 
 * @author xdurvak
 */

public class PrepareLocalEiffelSchemas {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrepareLocalEiffelSchemas.class);

    private String httpProxyUrl;
    private String httpProxyPort;
    private String httpProxyUsername;
    private String httpProxyPassword;

    /**
     * This method is used to clone repository from github using the URL and branch to local destination folder.
     * 
     * @param repoURL
     *            repository url to clone.
     * @param branch
     *            specific branch name of the repository to clone
     * @param localEiffelRepoPath
     *            destination path to clone the repo.
     */
    private void cloneEiffelRepo(String repoURL, String branch, File localEiffelRepoPath) {
        Git localGitRepo = null;
        if (getProxy() != null) {
            setProxy();
            /*
             * String proxy = getProxy().address().toString(); LOGGER.info(proxy);
             */
        }
        // checking for repository exists or not in the localEiffelRepoPath
        if (!localEiffelRepoPath.exists()) {
            try {
                // cloning github repository by using URL and branch name into local
                localGitRepo = Git.cloneRepository().setURI(repoURL).setBranch("master").setDirectory(localEiffelRepoPath).call();
                localGitRepo.checkout().setName(branch).call();
            } catch (Exception e) {
                LOGGER.info("please check proxy settings if proxy enabled update proxy.properties to fetch changes behind proxy");
                e.printStackTrace();
            }
        } else {
            // If required repository already exists
            try {
                localGitRepo = Git.open(localEiffelRepoPath);

                // Reset to normal if uncommitted changes are present
                localGitRepo.reset().call();

                //Checkout to master before pull the changes
                localGitRepo.checkout().setName(EiffelConstants.MASTER).call();

                // To get the latest changes from remote repository.
                localGitRepo.pull().call();

                //checkout to input branch after changes pulled into local
                localGitRepo.checkout().setName(branch).call();

            } catch (Exception e) {
                LOGGER.info("please check proxy settings if proxy enabled update proxy.properties file to clone behind proxy");
                e.printStackTrace();
            }
        }
        StoredConfig gitConfig = localGitRepo.getRepository().getConfig();
        gitConfig.setString("remote", "origin", "proxy", getProxy().address().toString());
        try {
            gitConfig.save();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setProxy() {
        ProxySelector.setDefault(new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return Arrays.asList(getProxy());
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                if (uri == null || sa == null || ioe == null) {
                    throw new IllegalArgumentException("Arguments can not be null.");
                }
            }

        });

    }

    public Proxy getProxy() {
        if (!httpProxyUrl.isEmpty() && !httpProxyPort.isEmpty()) {
            InetSocketAddress socket = InetSocketAddress.createUnresolved("hubsekiproxy.rnd.ericsson.se", 8080);
            if (!httpProxyUsername.isEmpty() && !httpProxyPassword.isEmpty()) {
                Authenticator authenticator = new Authenticator() {

                    public PasswordAuthentication getPasswordAuthentication() {
                        LOGGER.info("proxy authentication called");
                        return (new PasswordAuthentication(httpProxyUsername, httpProxyPassword.toCharArray()));
                    }
                };
                Authenticator.setDefault(authenticator);
            }
            return new Proxy(Proxy.Type.HTTP, socket);
        }
        return null;
    }

    /**
     * @param operationsRepoPath
     *            local operations repository url
     * @param eiffelRepoPath
     *            local eiffel repository url
     */
    private void copyOperationSchemas(String operationsRepoPath, String eiffelRepoPath) {
        File operationSchemas = new File(operationsRepoPath + File.separator + EiffelConstants.SCHEMA_LOCATION);
        File eiffelSchemas = new File(eiffelRepoPath + File.separator + EiffelConstants.SCHEMA_LOCATION);
        if (operationSchemas.isDirectory()) {
            try {
                FileUtils.copyDirectory(operationSchemas, eiffelSchemas);
            } catch (IOException e) {
                System.out.println("Exception occured while copying schemas from operations repository to eiffel repository");
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        PrepareLocalEiffelSchemas prepareLocalSchema = new PrepareLocalEiffelSchemas();

        //Proxy related information
        ResourceBundle links = ResourceBundle.getBundle("proxy", Locale.getDefault());
        prepareLocalSchema.httpProxyUrl = links.getString("http.proxy.url");
        prepareLocalSchema.httpProxyPort = links.getString("http.proxy.port");
        prepareLocalSchema.httpProxyUsername = links.getString("http.proxy.username");
        prepareLocalSchema.httpProxyPassword = links.getString("http.proxy.password");
        
        String eiffelRepoUrl = args[0];
        String eiffelRepoBranch = args[1];
        String operationRepoUrl = args[2];
        String operationRepoBranch = args[3];

        File localEiffelRepoPath = new File(System.getProperty(EiffelConstants.USER_HOME) + File.separator + EiffelConstants.EIFFEL);
        File localOperationsRepoPath = new File(
                System.getProperty(EiffelConstants.USER_HOME) + File.separator + EiffelConstants.OPERATIONS_REPO_NAME);

        // Clone Eiffel Repo from GitHub 
        prepareLocalSchema.cloneEiffelRepo(eiffelRepoUrl, eiffelRepoBranch, localEiffelRepoPath);

        //Clone Eiffel operations Repo from GitHub 
        prepareLocalSchema.cloneEiffelRepo(operationRepoUrl, operationRepoBranch, localOperationsRepoPath);

        //Copy operations repo Schemas to location where Eiffel repo schemas available
        prepareLocalSchema.copyOperationSchemas(localOperationsRepoPath.getAbsolutePath(), localEiffelRepoPath.getAbsolutePath());

        // Read and Load JsonSchemas from Cloned Directory
        LocalRepo localRepo = new LocalRepo(localEiffelRepoPath);
        localRepo.readSchemas();

        ArrayList<String> jsonEventNames = localRepo.getJsonEventNames();
        ArrayList<File> jsonEventSchemas = localRepo.getJsonEventSchemas();

        // Schema changes 
        SchemaFile schemaFile = new SchemaFile();

        // Iterate the Each jsonSchema file to Add and Modify the necessary properties 
        if (jsonEventNames != null && jsonEventSchemas != null) {
            for (int i = 0; i < jsonEventNames.size(); i++) {
                schemaFile.modify(jsonEventSchemas.get(i), jsonEventNames.get(i));
            }
        }
    }
}