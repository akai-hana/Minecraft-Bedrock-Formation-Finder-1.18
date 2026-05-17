> [!NOTE]
> Original project by [Developer-Mike](https://github.com/Developer-Mike).
>
> Forked from [Developer-Mike/minecraft-Bedrock-Formation-Finder-1.18](https://github.com/Developer-Mike/Minecraft-Bedrock-Formation-Finder-1.18). Forked by [akai-hana](https://github.com/akai-hana).
>
> ---
>
> This fork has the following objectives:
> 1. To optimize the program's runtime performance to its maximum possible extent.
> 2. To improve the program by extending its capabilities with new functionalities.
> 3. To add an easy-to-use GUI to run the program through, instead of losing braincells trying to manually formulate each equivalent command manually.

# Minecraft-Bedrock-Formation-Finder-1.18
Tool to find any bedrock formation in a 1.18 minecraft world.

## Usage
`java -jar bedrockformation.jar seed x:z type [x,y,z:bedrock]`
- seed (long)
  - Seed of your World
- x, z (int)
  - X and Z search center
- type (enum)
  - floor -> Searches on Bedrock floor
  - roof -> Searches on Bedrock roof
- Array of formation
  - x, y, z
    - Location of state
  - bedrock (enum)
    - 1 -> Bedrock wanted
    - 0 -> No Bedrock wanted

Sample:
`java -jar bedrockformation.jar 124352345 0:0 floor 0,-63,0:1 1,-62,0:1 0,-63,1:0`
