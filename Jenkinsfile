pipeline {
    agent any
    
    tools {
        maven 'Maven 3.9'
        jdk 'JDK 21'
    }
    
    environment {
        APP_NAME = 'java-demoapp'
        APP_VERSION = '1.0.0'
        DOCKER_REGISTRY = 'ghcr.io'
        DOCKER_IMAGE = "${DOCKER_REGISTRY}/${DOCKER_REPO}/${APP_NAME}"
        DOCKER_CREDENTIALS_ID = 'docker-registry-credentials'
        MAVEN_OPTS = '-Xmx1024m -XX:MaxPermSize=256m'
        SONAR_HOST_URL = "${env.SONAR_HOST_URL ?: 'http://sonarqube:9000'}"
        SONAR_CREDENTIALS_ID = 'sonarqube-token'
    }
    
    options {
        buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '5'))
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
        skipDefaultCheckout()
    }
    
    stages {
        stage('Checkout') {
            steps {
                script {
                    checkout scm
                    env.GIT_COMMIT_SHORT = sh(
                        script: "git rev-parse --short HEAD",
                        returnStdout: true
                    ).trim()
                    env.BUILD_TAG = "${APP_VERSION}-${env.GIT_COMMIT_SHORT}-${env.BUILD_NUMBER}"
                }
            }
        }
        
        stage('Validate') {
            steps {
                sh 'mvn validate'
            }
        }
        
        stage('Compile') {
            steps {
                sh 'mvn clean compile'
            }
        }
        
        stage('Test') {
            parallel {
                stage('Unit Tests') {
                    steps {
                        sh 'mvn test'
                    }
                    post {
                        always {
                            junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
                        }
                    }
                }
                
                stage('Code Style Check') {
                    steps {
                        sh 'mvn checkstyle:check'
                    }
                    post {
                        always {
                            recordIssues(
                                enabledForFailure: true,
                                tools: [checkStyle(pattern: '**/target/checkstyle-result.xml')]
                            )
                        }
                    }
                }
            }
        }
        
        stage('Code Quality Analysis') {
            when {
                branch 'main'
            }
            steps {
                echo "🔍 Code Quality Analysis stage - SonarQube scan would run here"
                echo "Project: ${APP_NAME}, Version: ${APP_VERSION}"
            }
        }
        
        stage('Quality Gate') {
            when {
                branch 'main'
            }
            steps {
                echo "✅ Quality Gate stage - Would check SonarQube results here"
            }
        }
        
        stage('Package') {
            steps {
                sh 'mvn package -DskipTests'
            }
            post {
                success {
                    archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
                }
            }
        }
        

