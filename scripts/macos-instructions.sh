#!/usr/bin/env bash

# This script requires yq (https://github.com/mikefarah/yq) and can be installed with this command
# snap install yq

# This script does two things
# 1. Modify README.md to include the instructions specified in $INSTRUCTION_FILE
# 2. Create a github action running all of the code specified in $INSTRUCTION_FILE

INSTRUCTION_FILE=macos_instructions.yml
OUTPUT_FILE_TEXT=macos-instructions.md
ACTION_FILE=.github/workflows/macos_installation_instructions.yml
README=README.md

# Initalize output file
> "$OUTPUT_FILE_TEXT"

NUMBER_OF_INSTRUCTIONS=$(yq '.setupInformation | length' "$INSTRUCTION_FILE")


# Add information in actionOnlyInformation to $ACTION_FILE
yq '.actionOnlyInformation' "$INSTRUCTION_FILE" > "$ACTION_FILE"


export i=0
export CODE_FOR_ACTION=""
for (( i=0; i<"$NUMBER_OF_INSTRUCTIONS"; i++ ))
do
   # Get key
   key=$(yq '.setupInformation.[env(i)] | keys' "$INSTRUCTION_FILE" | tr -cd '[:alnum:]')

   if [ "$key" == "text" ]; then
      yq  '.setupInformation.[env(i)].text' "$INSTRUCTION_FILE"  >> $OUTPUT_FILE_TEXT

   elif [ "$key" == "newLine" ]; then
      NUMBER_LINES_TO_PRINT=$(yq '.setupInformation[env(i)].newLine' "$INSTRUCTION_FILE")
      yes "" | head -n "$NUMBER_LINES_TO_PRINT" >> "$OUTPUT_FILE_TEXT"

   elif [ "$key" == "code" ]; then
     STRING_TO_PRINT=$(yq  '.setupInformation.[env(i)].code.run' "$INSTRUCTION_FILE")
     {
      echo "\`\`\`"
      echo -e "$STRING_TO_PRINT"
      echo "\`\`\`"
     } >> "$OUTPUT_FILE_TEXT"
     CODE_FOR_ACTION=$(yq -o json '.setupInformation.[env(i)].code' "$INSTRUCTION_FILE")
     yq -i '.jobs.build.steps += eval(strenv(CODE_FOR_ACTION))' "$ACTION_FILE"

   else
       echo "ERROR: Did not recognise ${key}"
       exit 1
   fi

done

TEMP_FILE=temp.txt

# Delete current instructions from README.md
sed -n '1,/DO_NOT_DELETE_START_MACOS_INSTRUCTIONS/p;/DO_NOT_DELETE_END_MACOS_INSTRUCTIONS/,$p' "$README" > "$TEMP_FILE"

cat "$TEMP_FILE" > README.md

# Add new instructions to README.md
sed '/DO_NOT_DELETE_END_MACOS_INSTRUCTIONS/e cat instructions.md' "$README" > "$TEMP_FILE"
cat "$TEMP_FILE" > "$README"