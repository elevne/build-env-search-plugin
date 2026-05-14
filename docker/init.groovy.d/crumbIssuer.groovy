import jenkins.model.*

def instance = Jenkins.instance
instance.setCrumbIssuer(null)
