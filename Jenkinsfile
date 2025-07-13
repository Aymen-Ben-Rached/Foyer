pipeline {
    agent any

    environment {
        MAVEN_OPTS = '-Dmaven.test.failure.ignore=false'
        JMETER_TEST_FILE = 'load-test.jmx' // Update to 'jmeter/load-test.jmx' if in a subdirectory
        JMETER_REPORT_DIR = 'jmeter-report'
        JMETER_SUMMARY_FILE = 'jmeter-report/summary.txt'
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
                sh 'docker build -t aymen/foyer:latest .'
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

        stage('Wait for Application') {
            steps {
                echo "Waiting for application to be ready"
                sh '''
                    for i in {1..30}; do
                        if curl -s -f http://localhost:8086/Foyer/bloc/findAll > /dev/null; then
                            echo "Application is up!"
                            exit 0
                        fi
                        echo "Waiting for application... ($i/30)"
                        sleep 2
                    done
                    echo "Application failed to start within 60 seconds"
                    exit 1
                '''
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
                        justb4/jmeter:5.4.3 \
                        -n -t /jmeter/${env.JMETER_TEST_FILE} \
                        -l /jmeter/${env.JMETER_REPORT_DIR}/results.jtl \
                        -e -o /jmeter/${env.JMETER_REPORT_DIR}/html
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
Console Output: ${env.BUILD_URL}console

JMeter Test Results:
${jmeterSummary}
                    """
                )
            }
        }
    }
}