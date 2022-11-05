/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/** A standard release pipeline for OpenSearch projects including generic triggers. A tag or a draft release can be used as a trigger using this library. The defaults are all set to trigger via a draft release. If the release is successful, the release can be published by using right params.
@param Map args = [:] args A map of the following parameters
@param body <Required> - A closure containing release steps to be executed in release stage.
@param args.tokenIdCredential <Required> - Credential id containing token for trigger authentication
@param args.overrideAgent <Optional> - Jenkins agent label to override the default ('Jenkins-Agent-AL2-X64-C54xlarge-Docker-Host')
@param args.overrideDockerImage <Optional> - Docker image to override the default ('opensearchstaging/ci-runner:release-centos7-clients-v1')
@param args.jsonValue <Optional> - Json value retrieved from payload of the webhook. Defaults to '$.release.tag_name'
@param args.causeString <Optional> - String mentioning why the workflow was triggered. Defaults to 'A tag was cut on GitHub repository causing this workflow to run'
@param args.regexpFilterText <Optional> - Variable to apply regular expression on. Defaults to '$isDraft'
@param.regexpFilterExpression <Optional> - Regular expression to test on the evaluated text specified. Defaults to ''
@param.publishRelease <Optional> - If set to true the release that triggered the job will be published on GitHub.
*/

void call(Map args = [:], Closure body) {
    pipeline {
        agent
        {
            docker {
                label args.overrideAgent ?: 'Jenkins-Agent-AL2-X64-C54xlarge-Docker-Host'
                image args.overrideDockerImage ?: 'opensearchstaging/ci-runner:release-centos7-clients-v1'
                alwaysPull true
            }
        }
        options {
            timeout(time: 1, unit: 'HOURS')
        }
        triggers {
            GenericTrigger(
                genericVariables: [
                    [key: 'ref', value: (args.jsonValue ?: '$.release.tag_name')],
                    [key: 'isDraft', value: '$.release.draft'],
                    [key: 'release_url', value: '$.release.url']
                ],
                tokenCredentialId: args.tokenIdCredential,
                causeString: args.causeString ?: 'A tag was cut on GitHub repository causing this workflow to run',
                printContributedVariables: false,
                printPostContent: false,
                regexpFilterText: (args.regexpFilterText ?: '$isDraft'),
                regexpFilterExpression: (args.regexpFilterExpression ?: 'true')
            )
        }
        environment {
            tag = "$ref"
        }
        stages {
            stage('Release') {
                steps {
                    script {
                        body()
                    }
                }
            }
        }
        post {
            success {
                script {
                    if (args.publishRelease && release_url != null) {
                        withCredentials([usernamePassword(credentialsId: 'jenkins-github-bot-token', usernameVariable: 'GITHUB_USER', passwordVariable: 'GITHUB_TOKEN')]) {
                            sh "curl -X PATCH -H 'Accept: application/vnd.github+json' -H 'Authorization: Bearer ${GITHUB_TOKEN}' ${release_url} -d '{\"tag_name\":\"${tag}\",\"draft\":false,\"prerelease\":false}'"
                        }
                    }
                }
            }
            always {
                script {
                    postCleanup()
                }
            }
        }
    }
}