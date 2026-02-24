pipeline {
    agent any
    
    tools {
        maven 'Maven 3.9'
        jdk 'JDK 17'
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
                script {
                    withSonarQubeEnv(credentialsId: "${SONAR_CREDENTIALS_ID}") {
                        sh """
                            mvn sonar:sonar \
                                -Dsonar.projectKey=${APP_NAME} \
                                -Dsonar.projectName='${APP_NAME}' \
                                -Dsonar.projectVersion=${APP_VERSION}
                        """
                    }
                }
            }
        }
        
        stage('Quality Gate') {
            when {
                branch 'main'
            }
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
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
        
        stage('Build Docker Image') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                    branch pattern: 'release/.*'
                }
            }
            steps {
                script {
                    docker.build("${DOCKER_IMAGE}:${BUILD_TAG}")
                    docker.build("${DOCKER_IMAGE}:latest")
                }
            }
        }
        
        stage('Security Scan') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                }
            }
            parallel {
                stage('Dependency Check') {
                    steps {
                        sh 'mvn org.owasp:dependency-check-maven:check'
                    }
                    post {
                        always {
                            publishHTML(target: [
                                allowMissing: true,
                                alwaysLinkToLastBuild: true,
                                keepAll: true,
                                reportDir: 'target',
                                reportFiles: 'dependency-check-report.html',
                                reportName: 'OWASP Dependency Check'
                            ])
                        }
                    }
                }
                
                stage('Container Scan') {
                    steps {
                        script {
                            sh """
                                trivy image \
                                    --severity HIGH,CRITICAL \
                                    --exit-code 0 \
                                    --format json \
                                    --output trivy-report.json \
                                    ${DOCKER_IMAGE}:${BUILD_TAG}
                            """
                        }
                    }
                    post {
                        always {
                            archiveArtifacts artifacts: 'trivy-report.json', allowEmptyArchive: true
                        }
                    }
                }
            }
        }
        
        stage('Push Docker Image') {
            when {
                branch 'main'
            }
            steps {
                script {
                    docker.withRegistry("https://${DOCKER_REGISTRY}", "${DOCKER_CREDENTIALS_ID}") {
                        docker.image("${DOCKER_IMAGE}:${BUILD_TAG}").push()
                        docker.image("${DOCKER_IMAGE}:latest").push()
                    }
                }
            }
        }
        
        stage('Deploy to Dev') {
            when {
                branch 'develop'
            }
            steps {
                script {
                    echo "Deploying to Development environment..."
                    // Add your deployment logic here
                    // Example: kubectl, helm, terraform, etc.
                }
            }
        }
        
        stage('Deploy to Staging') {
            when {
                branch 'main'
            }
            steps {
                script {
                    echo "Deploying to Staging environment..."
                    // Add your deployment logic here
                }
            }
        }
        
        stage('Deploy to Production') {
            when {
                allOf {
                    branch 'main'
                    expression { return params.DEPLOY_TO_PROD == true }
                }
            }
            steps {
                timeout(time: 15, unit: 'MINUTES') {
                    input message: 'Deploy to Production?', ok: 'Deploy'
                }
                script {
                    echo "Deploying to Production environment..."
                    // Add your production deployment logic here
                }
            }
        }
    }
    
    post {
        always {
            cleanWs(
                deleteDirs: true,
                patterns: [
                    [pattern: 'target/**', type: 'INCLUDE'],
                    [pattern: '.m2/**', type: 'INCLUDE']
                ]
            )
        }
        success {
            script {
                if (env.BRANCH_NAME == 'main') {
                    emailext(
                        subject: "✅ SUCCESS: ${env.JOB_NAME} - Build #${env.BUILD_NUMBER}",
                        body: """
                            Build succeeded for ${env.JOB_NAME}
                            Build Number: ${env.BUILD_NUMBER}
                            Build URL: ${env.BUILD_URL}
                            Git Commit: ${env.GIT_COMMIT_SHORT}
                        """,
                        to: '${DEFAULT_RECIPIENTS}'
                    )
                }
            }
        }
        failure {
            emailext(
                subject: "❌ FAILURE: ${env.JOB_NAME} - Build #${env.BUILD_NUMBER}",
                body: """
                    Build failed for ${env.JOB_NAME}
                    Build Number: ${env.BUILD_NUMBER}
                    Build URL: ${env.BUILD_URL}
                    Git Commit: ${env.GIT_COMMIT_SHORT}
                    
                    Please check the console output for details.
                """,
                to: '${DEFAULT_RECIPIENTS}'
            )
        }
        unstable {
            emailext(
                subject: "⚠️ UNSTABLE: ${env.JOB_NAME} - Build #${env.BUILD_NUMBER}",
                body: """
                    Build is unstable for ${env.JOB_NAME}
                    Build Number: ${env.BUILD_NUMBER}
                    Build URL: ${env.BUILD_URL}
                """,
                to: '${DEFAULT_RECIPIENTS}'
            )
        }
    }
}
