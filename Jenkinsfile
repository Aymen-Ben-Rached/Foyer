pipeline {
    agent any

    environment {
        MAVEN_OPTS = '-Dmaven.test.failure.ignore=false'
        JMETER_TEST_FILE = 'load-test.jmx' // Update to 'jmeter/load-test.jmx' if in a subdirectory
        JMETER_REPORT_DIR = 'jmeter-report'
        JMETER_SUMMARY_FILE = 'jmeter-report/summary.txt'
        APP_HOST = 'localhost'
        APP_PORT = '8086'
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

        stage('Maven - Clean & Compile') {
            steps {
                echo "Running mvn clean compile"
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

        stage('Start Docker Compose Stack') {
            steps {
                echo "Starting Docker Compose stack (app + MySQL)"
                sh '''
                    docker-compose down --remove-orphans || true
                    docker ps -aqf "name=mysql" | xargs -r docker rm -f || true
                    docker ps -aqf "name=foyer-app" | xargs -r docker rm -f || true
                    docker-compose up -d
                '''
            }
        }

        stage('Debug Containers') {
            steps {
                echo "Capturing container logs and status for debugging"
                sh '''
                    docker ps -a > docker-ps.log
                    docker logs mysql > mysql.log 2>&1 || echo "No logs available for mysql" >> mysql.log
                    docker logs foyer-app > foyer-app.log 2>&1 || echo "No logs available for foyer-app" >> foyer-app.log
                '''
                archiveArtifacts artifacts: '*.log', allowEmptyArchive: true
            }
        }

        stage('Wait for Application') {
            steps {
                echo "Waiting for MySQL and application to be ready"
                script {
                    // Check MySQL health
                    def mysqlReady = sh(script: '''
                        for i in {1..60}; do
                            if docker exec mysql mysqladmin ping -h localhost -uroot -ppassword > /dev/null 2>&1; then
                                echo "MySQL is up!"
                                exit 0
                            fi
                            echo "Waiting for MySQL... ($i/60)"
                            sleep 2
                        done
                        echo "MySQL failed to start within 120 seconds"
                        exit 1
                    ''', returnStatus: true)
                    if (mysqlReady != 0) {
                        error "MySQL failed to start"
                    }
                    // Check foyer-app health with debug output
                    def appReady = sh(script: '''
                        echo "Testing endpoint http://${APP_HOST}:${APP_PORT}/Foyer/bloc/findAll" > curl-debug.log
                        for i in {1..90}; do
                            HTTP_CODE=$(curl -s -w "%{http_code}" -o curl-output.log http://${APP_HOST}:${APP_PORT}/Foyer/bloc/findAll)
                            if [ "$HTTP_CODE" -eq 200 ]; then
                                echo "Application is up on ${APP_HOST}:${APP_PORT}! HTTP Status: $HTTP_CODE" | tee -a curl-debug.log
                                cat curl-output.log >> curl-debug.log
                                exit 0
                            fi
                            echo "Waiting for application on ${APP_HOST}:${APP_PORT}... ($i/90) HTTP Status: $HTTP_CODE" | tee -a curl-debug.log
                            cat curl-output.log >> curl-debug.log
                            sleep 2
                        done
                        echo "Application not reachable on ${APP_HOST}:${APP_PORT}. Trying container IP..." | tee -a curl-debug.log
                        APP_IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' foyer-app)
                        echo "Container IP: ${APP_IP}" >> curl-debug.log
                        for i in {1..90}; do
                            HTTP_CODE=$(curl -s -w "%{http_code}" -o curl-output.log http://${APP_IP}:${APP_PORT}/Foyer/bloc/findAll)
                            if [ "$HTTP_CODE" -eq 200 ]; then
                                echo "Application is up on ${APP_IP}:${APP_PORT}! HTTP Status: $HTTP_CODE" | tee -a curl-debug.log
                                cat curl-output.log >> curl-debug.log
                                echo "${APP_IP}" > app_ip.txt
                                exit 0
                            fi
                            echo "Waiting for application on ${APP_IP}:${APP_PORT}... ($i/90) HTTP Status: $HTTP_CODE" | tee -a curl-debug.log
                            cat curl-output.log >> curl-debug.log
                            sleep 2
                        done
                        echo "Application failed to start within 180 seconds" | tee -a curl-debug.log
                        exit 1
                    ''', returnStatus: true)
                    sh 'cat curl-debug.log' // Log debug output to console
                    archiveArtifacts artifacts: 'curl-debug.log, curl-output.log', allowEmptyArchive: true
                    if (appReady != 0) {
                        error "Application failed to start"
                    }
                    // If container IP was used, update APP_HOST
                    if (fileExists('app_ip.txt')) {
                        env.APP_HOST = readFile('app_ip.txt').trim()
                    }
                }
            }
        }

        stage('Run JMeter Load Test') {
            steps {
                echo "Running JMeter load test in Docker"
                sh """
                    mkdir -p ${env.JMETER_REPORT_DIR}
                    chmod -R 777 ${env.JMETER_REPORT_DIR}
                    docker run --rm \
                        -v \${WORKSPACE}:/jmeter \
                        ealen/jmeter:5.4.3 \
                        -n -t /jmeter/${env.JMETER_TEST_FILE} \
                        -l /jmeter/${env.JMETER_REPORT_DIR}/results.jtl \
                        -e -o /jmeter/${env.JMETER_REPORT_DIR}/html \
                        -Jserver.host=${env.APP_HOST} -Jserver.port=${env.APP_PORT}
                    # Extract JMeter summary
                    echo "JMeter Test Summary" > ${env.JMETER_SUMMARY_FILE}
                    echo "-----------------" >> ${env.JMETER_SUMMARY_FILE}
                    total_requests=\$(wc -l < ${env.JMETER_REPORT_DIR}/results.jtl)
                    errors=\$(grep ',false,' ${env.JMETER_REPORT_DIR}/results.jtl | wc -l)
                    avg_response_time=\$(awk -F',' '{sum+=\$3} END {if (NR > 0) print sum/NR}' ${env.JMETER_REPORT_DIR}/results.jtl)
                    echo "Total Requests: \$total_requests" >> ${env.JMETER_SUMMARY_FILE}
                    echo "Errors: \$errors" >> ${env.JMETER_SUMMARY_FILE}
                    echo "Average Response Time (ms): \$avg_response_time" >> ${env.JMETER_SUMMARY_FILE}
                """
                archiveArtifacts artifacts: "${env.JMETER_REPORT_DIR}/**", fingerprint: true
            }
        }
    }

    post {
        always {
            script {
                def jmeterSummary = ""
                if (fileExists("${env.JMETER_SUMMARY_FILE}")) {
                    jmeterSummary = readFile("${env.JMETER_SUMMARY_FILE}").trim()
                } else {
                    jmeterSummary = "JMeter summary not available (test may have failed or summary file not generated)."
                }
                mail (
                    to: 'aymenbenrached2002@gmail.com',
                    subject: "Jenkins Build ${currentBuild.currentResult}: Job ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                    body: """
Build Status: ${currentBuild.currentResult}
Job: ${env.JOB_NAME}
Build Number: ${env.BUILD_NUMBER}
Build URL: ${env.BUILD_URL}
JaCoCo Coverage Report: ${env.BUILD_URL}artifact/target/site/jacoco/index.html
JMeter Load Test Report: ${env.BUILD_URL}artifact/${env.JMETER_REPORT_DIR}/html/index.html
MySQL Logs: ${env.BUILD_URL}artifact/mysql.log
Foyer App Logs: ${env.BUILD_URL}artifact/foyer-app.log
Curl Debug Log: ${env.BUILD_URL}artifact/curl-debug.log
Console Output: ${env.BUILD_URL}console

JMeter Test Results:
${jmeterSummary}
                    """
                )
            }
        }
    }
}