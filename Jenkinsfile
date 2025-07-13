pipeline {
    agent any

    environment {
        MAVEN_OPTS = '-Dmaven.test.failure.ignore=false'
    }

    stages {
        stage('Checkout') {
            steps {
                echo "Getting project from GitHub"
                git url: 'https://github.com/Aymen-Ben-Rached/Foyer.git',
                    branch: 'master',
                    credentialsId: 'github-pat'
            }
        }

        stage('Clean target directory') {
            steps {
                echo "Resetting permissions and cleaning target directory"
                sh '''
                    # Change ownership recursively to current user to avoid permission issues
                    chown -R $(id -u):$(id -g) target || true
                    rm -rf target || true
                '''
            }
        }

        stage('Maven - Clean & Compile') {
            steps {
                echo "Running Maven clean and compile"
                configFileProvider([configFile(fileId: 'settings_xml', variable: 'MAVEN_SETTINGS')]) {
                    sh 'mvn clean compile -s $MAVEN_SETTINGS'
                }
            }
        }

        stage('Tests & Coverage') {
            steps {
                echo "Running tests and generating JaCoCo report"
                configFileProvider([configFile(fileId: 'settings_xml', variable: 'MAVEN_SETTINGS')]) {
                    sh 'mvn test jacoco:report -s $MAVEN_SETTINGS'
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                echo "Running SonarQube analysis"
                withSonarQubeEnv('sonar') {
                    withCredentials([string(credentialsId: 'sonar_token', variable: 'SONAR_TOKEN')]) {
                        sh '''
                            mvn sonar:sonar \
                                -Dsonar.projectKey=Foyer \
                                -Dsonar.login=$SONAR_TOKEN \
                                -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
                        '''
                    }
                }
            }
        }

        stage('Publish JUnit Results') {
            steps {
                junit 'target/surefire-reports/*.xml'
            }
        }

        stage('Archive Coverage Report') {
            steps {
                echo "Archiving JaCoCo HTML report"
                archiveArtifacts artifacts: 'target/site/jacoco/**', fingerprint: true
            }
        }

        stage('Deploy to Nexus') {
            steps {
                echo "Deploying to Nexus"
                withCredentials([usernamePassword(credentialsId: 'nexus-creds', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS')]) {
                    configFileProvider([configFile(fileId: 'settings', variable: 'MAVEN_SETTINGS')]) {
                        sh '''
                            mvn deploy \
                                -s $MAVEN_SETTINGS \
                                -Dnexus.username=$NEXUS_USER \
                                -Dnexus.password=$NEXUS_PASS
                        '''
                    }
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                echo "Building Docker image"
                sh 'docker build -t aymenbr/foyer:latest .'
            }
        }

        /*
        stage('Push Docker Image') {
            steps {
                echo "Logging in to Docker Hub and pushing image"
                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds', usernameVariable: 'DOCKERHUB_USERNAME', passwordVariable: 'DOCKERHUB_PASSWORD')]) {
                    sh '''
                        echo "$DOCKERHUB_PASSWORD" | docker login -u "$DOCKERHUB_USERNAME" --password-stdin
                        docker push aymenbr/foyer:latest
                    '''
                }
            }
        }
        */

        stage('Start Docker Compose Stack') {
            steps {
                echo "Starting Docker Compose stack (app + MySQL)"
                sh '''
                    docker-compose down --remove-orphans || true
                    docker ps -aqf "name=mysql" | xargs -r docker rm -f || true
                    docker ps -aqf "name=foyer-app" | xargs -r docker rm -f || true
                    docker-compose up -d
                    sleep 15
                '''
            }
        }

        stage('Load Test with JMeter') {
            steps {
                echo "Running JMeter load test"
                sh '''
                    mkdir -p target/jmeter
                    docker run --rm -v "$PWD":/test -w /test justb4/jmeter:5.4 \
                        -n -t load-test.jmx -l target/jmeter/results.jtl -e -o target/jmeter/html

                    # Fix permissions on generated JMeter files
                    chown -R $(id -u):$(id -g) target/jmeter || true
                '''
            }
        }

        stage('Archive JMeter Results') {
            steps {
                echo "Archiving JMeter results"
                archiveArtifacts artifacts: 'target/jmeter/**', fingerprint: true
            }
        }
    }

    post {
        always {
            script {
                def jmeterSummary = sh(script: '''
                    if [ -f target/jmeter/results.jtl ]; then
                        echo "JMeter test completed."
                        total=$(grep -c "<httpSample" target/jmeter/results.jtl || true)
                        failed=$(grep -c "s=\\"false\\"" target/jmeter/results.jtl || true)
                        echo "Total Requests: $total"
                        echo "Failed Requests: $failed"
                    else
                        echo "No JMeter results found."
                    fi
                ''', returnStdout: true).trim()

                mail (
                    to: 'aymenbenrached2002@gmail.com',
                    subject: "Jenkins Build ${currentBuild.currentResult}: Job ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                    body: """
Build Status: ${currentBuild.currentResult}
Job: ${env.JOB_NAME}
Build Number: ${env.BUILD_NUMBER}
Build URL: ${env.BUILD_URL}
JaCoCo Coverage Report: ${env.BUILD_URL}artifact/target/site/jacoco/index.html
Console Output: ${env.BUILD_URL}console

JMeter Summary:
${jmeterSummary}
                    """
                )
            }
        }
    }
}
