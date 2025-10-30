package com.therighthandapp.agentmesh.github;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * GitHub webhook event payload
 */
public class GitHubEvent {
    private String action;
    private GitHubIssue issue;
    private GitHubPullRequest pullRequest;
    private GitHubComment comment;
    private GitHubRepository repository;
    private GitHubUser sender;

    // Getters and setters
    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public GitHubIssue getIssue() {
        return issue;
    }

    public void setIssue(GitHubIssue issue) {
        this.issue = issue;
    }

    public GitHubPullRequest getPullRequest() {
        return pullRequest;
    }

    public void setPullRequest(GitHubPullRequest pullRequest) {
        this.pullRequest = pullRequest;
    }

    public GitHubComment getComment() {
        return comment;
    }

    public void setComment(GitHubComment comment) {
        this.comment = comment;
    }

    public GitHubRepository getRepository() {
        return repository;
    }

    public void setRepository(GitHubRepository repository) {
        this.repository = repository;
    }

    public GitHubUser getSender() {
        return sender;
    }

    public void setSender(GitHubUser sender) {
        this.sender = sender;
    }

    public static class GitHubIssue {
        private Long number;
        @JsonProperty("node_id")
        private String nodeId;
        private String title;
        private String body;
        private String state;
        private List<GitHubLabel> labels;
        private GitHubUser user;

        public Long getNumber() {
            return number;
        }

        public void setNumber(Long number) {
            this.number = number;
        }

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public List<GitHubLabel> getLabels() {
            return labels;
        }

        public void setLabels(List<GitHubLabel> labels) {
            this.labels = labels;
        }

        public GitHubUser getUser() {
            return user;
        }

        public void setUser(GitHubUser user) {
            this.user = user;
        }

        public boolean hasLabel(String labelName) {
            return labels != null && labels.stream()
                    .anyMatch(l -> labelName.equals(l.getName()));
        }
    }

    public static class GitHubPullRequest {
        private Long number;
        private String title;
        private String body;
        private String state;
        @JsonProperty("html_url")
        private String htmlUrl;
        private boolean merged;

        public Long getNumber() {
            return number;
        }

        public void setNumber(Long number) {
            this.number = number;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getHtmlUrl() {
            return htmlUrl;
        }

        public void setHtmlUrl(String htmlUrl) {
            this.htmlUrl = htmlUrl;
        }

        public boolean isMerged() {
            return merged;
        }

        public void setMerged(boolean merged) {
            this.merged = merged;
        }
    }

    public static class GitHubComment {
        private Long id;
        private String body;
        private GitHubUser user;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public GitHubUser getUser() {
            return user;
        }

        public void setUser(GitHubUser user) {
            this.user = user;
        }
    }

    public static class GitHubLabel {
        private String name;
        private String color;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getColor() {
            return color;
        }

        public void setColor(String color) {
            this.color = color;
        }
    }

    public static class GitHubRepository {
        private String name;
        @JsonProperty("full_name")
        private String fullName;
        private GitHubUser owner;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public GitHubUser getOwner() {
            return owner;
        }

        public void setOwner(GitHubUser owner) {
            this.owner = owner;
        }
    }

    public static class GitHubUser {
        private String login;
        @JsonProperty("node_id")
        private String nodeId;

        public String getLogin() {
            return login;
        }

        public void setLogin(String login) {
            this.login = login;
        }

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
        }
    }
}

