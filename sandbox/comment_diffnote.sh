project=tomas.bjerre85%2Fviolations-test
mergeRequestIid=1
url=https://gitlab.com/api/v4/projects/$project/merge_requests/$mergeRequestIid/discussions

curl $url --request POST --header "PRIVATE-TOKEN: $GITLAB_TOKEN" --data "$(cat comment_diffnote.txt | tr '\n' '&')" -v 2>&1
