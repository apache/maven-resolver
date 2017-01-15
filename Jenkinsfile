/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

properties([buildDiscarder(logRotator(artifactNumToKeepStr: '5', numToKeepStr: env.BRANCH_NAME=='master'?'10':'5'))])

node('ubuntu') {
    try {
        stage('Checkout') 
            def MAVEN_BUILD=tool name: 'Maven 3.3.9', type: 'hudson.tasks.Maven$MavenInstallation'
            echo "Driving build and unit tests using Maven $MAVEN_BUILD"
            def JAVA7_HOME=tool name: 'JDK 1.7 (latest)', type: 'hudson.model.JDK'
            echo "Running build and unit tests with Java $JAVA7_HOME"
            dir('build') {
                deleteDir()
            }
            dir('build') {
                checkout scm
            }
        stage('Build/Test')
            def WORK_DIR=pwd()
            dir('build') {
                try {
                    withEnv(["PATH+MAVEN=$MAVEN_BUILD/bin","PATH+JDK=$JAVA7_HOME/bin"]) {
                        sh "mvn clean verify -B -U -e -fae -V -Dmaven.test.failure.ignore=true -Dmaven.repo.local=$WORK_DIR/.repository"
                    }
                } finally {
                    junit allowEmptyResults: true, testResults:'**/target/*-reports/*.xml'
                    archiveArtifacts allowEmptyArchive: true, artifacts: '**/target/rat.txt'
                }
            }
    } finally {
        emailext body: "See ${env.BUILD_URL}", recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'FailingTestSuspectsRecipientProvider'], [$class: 'FirstFailingBuildSuspectsRecipientProvider']], replyTo: 'dev@maven.apache.org', subject: "Maven Resolver Jenkinsfile finished with ${currentBuild.result}", to: 'notifications@maven.apache.org'
    }
}
