# GitOps Hydrated Manifests Branch

This branch is dedicated to storing hydrated (fully rendered) Kubernetes manifest files, which are generated automatically from the Helm charts in this repository.

**Do not modify any files in this branch directly.** All files are produced by automation and any manual changes will be overwritten.

These manifests are intended to be consumed by ArgoCD for deployment purposes.
Please make all changes to the source Helm charts or configuration files in the main development branches; this branch will be updated as part of the CI/CD
