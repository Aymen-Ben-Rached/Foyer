pipeline {
    agent {
        docker {
            image 'maven:3.8.6-openjdk-11'
            args '-v $HOME/.m2:/root/.m2 --network foyer_default'
        }
    }

    environment {
        MAVEN_OPTS = '-Dmaven.test.failure.ignore=false'
        APP_HOST = 'foyer-app'
        APP_PORT = '8086'
    }

    stages {
        stage('Clean Workspace') {
            steps {
                cleanWs()
            }
        }

        stage('Checkout') {
            steps {
                echo "Getting project from GitHub"
                git url: 'https://github.com/Aymen-Ben-Rached/Foyer.git',
                    branch: 'master',
                    credentialsId: 'github-pat'
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
                    docker image prune -f || true
                    docker-compose up -d
                    sleep 15
                    echo "Checking containers..."
                    docker ps -a
                    for i in {1..30}; do
                        if curl -f http://$APP_HOST:$APP_PORT/health; then
                            echo "Application is up on $APP_HOST:$APP_PORT"
                            docker ps -a
                            break
                        fi
                        echo "Waiting for application..."
                        sleep 5
                    done
                    if ! curl -f http://$APP_HOST:$APP_PORT/health; then
                        echo "Application not responding"
                        docker-compose logs foyer-app
                        docker-compose logs mysql
                        docker ps -a
                        exit 1
                    fi
                '''
            }
        }

        stage('Load Test with JMeter') {
            agent {
                docker {
                    image 'justb4/jmeter:5.4.3'
                    args '-u $(id -u):$(id -g) --network foyer_default -v "$PWD:/test"'
                }
            }
            steps {
                echo "Running JMeter load test"
                sh '''
                    echo "Verifying connectivity to $APP_HOST:$APP_PORT"
                    curl -v http://$APP_HOST:$APP_PORT/health
                    mkdir -p target/jmeter
                    /opt/apache-jmeter-5.4.3/bin/jmeter -n -t load-test.jmx -l target/jmeter/results.jtl -e -o target/jmeter/html -j target/jmeter/jmeter.log
                    cat target/jmeter/jmeter.log || true
                '''
            }
        }

        stage('Archive JMeter Results') {
            steps {
                echo "Archiving JMeter results"
                archiveArtifacts artifacts: 'target/jmeter/**,target/jmeter/jmeter.log', fingerprint: true
            }
        }
    }

    post {
        always {
            sh 'docker-compose logs foyer-app > foyer-app.log'
            sh 'docker-compose logs mysql > mysql.log'
            sh 'docker ps -a > docker-ps-a.log'
            archiveArtifacts artifacts: '*.log', allowEmptyArchive: true
            sh 'docker-compose down --remove-orphans || true'
            script {
                def jmeterSummary = sh(script: '''
                    if [ -f target/jmeter/results.jtl ]; then
                        echo "JMeter test completed. Check the HTML report at ${BUILD_URL}artifact/target/jmeter/html/index.html for details."
                    else
                        echo "No JMeter results found. Check the JMeter log at ${BUILD_URL}artifact/target/jmeter/jmeter.log for errors."
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
JMeter HTML Report: ${env.BUILD_URL}artifact/target/jmeter/html/index.html
JMeter Log: ${env.BUILD_URL}artifact/target/jmeter/jmeter.log
Console Output: ${env.BUILD_URL}console
Application Log: ${env.BUILD_URL}artifact/foyer-app.log
MySQL Log: ${env.BUILD_URL}artifact/mysql.log
Docker Containers: ${env.BUILD_URL}artifact/docker-ps-a.log

JMeter Summary:
${jmeterSummary}
                    """
                )
            }
        }
    }
}
