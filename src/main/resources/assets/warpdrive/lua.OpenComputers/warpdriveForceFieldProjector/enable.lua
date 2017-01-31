local component = require("component")
local term = require("term")

if not component.isAvailable("warpdriveForceFieldProjector") then
  print("No force field projector detected")
else
  local projector = component.warpdriveForceFieldProjector
  projector.enable(true)
  os.sleep(1)
  status, isEnabled, isConnected, isPowered, shape, energy = projector.state()
  if isConnected then
    if isPowered then
      print("Projector is enabled")
    else
      print("Projector is missing a shape!")
    end
  else
    print("Projector is missing a beam frequency!")
  end
end

print("")