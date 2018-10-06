project=tomas.bjerre85%2Fviolations-test
mergeRequestIid=1
url=https://gitlab.com/api/v4/projects/$project/merge_requests/$mergeRequestIid/discussions

curl $url -H 'Content-Type", "application/json' -H "PRIVATE-TOKEN: $GITLAB_TOKEN" --data '@comment_diffnote.txt' -v 2>&1
