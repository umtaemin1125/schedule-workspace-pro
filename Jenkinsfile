@Library('') _

pipeline {
  agent any
  stages {
    stage('Run Monorepo Pipeline') {
      steps {
        load 'infra/jenkins/Jenkinsfile'
      }
    }
  }
}
