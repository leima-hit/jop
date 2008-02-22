# TCL File Generated by Component Editor 7.2 on:
# Sun Feb 10 20:19:22 CET 2008
# DO NOT MODIFY

set_source_file "jop_avalon.vhd"
set_module "jop_avalon"
set_module_description ""
set_module_property "editable" "true"
set_module_property "group" "JOP"
set_module_property "instantiateInSystemModule" "true"
set_module_property "libraries" [ list "ieee.std_logic_1164.all" "ieee.numeric_std.all" "std.standard.all" ]
set_module_property "simulationFiles" "
  "jop_avalon.vhd"
set_module_property "version" "1.0"

# Module parameters
add_parameter "addr_bits" "integer" "24" ""
add_parameter "jpc_width" "integer" "12" ""
add_parameter "block_bits" "integer" "4" ""

# Interface avalon_master_clock
add_interface "avalon_master_clock" "clock" "sink" "asynchronous"
# Ports in interface avalon_master_clock
add_port_to_interface "avalon_master_clock" "clk" "clk"
add_port_to_interface "avalon_master_clock" "reset" "reset"

# Interface global_signals_export
add_interface "global_signals_export" "conduit" "start" "asynchronous"
# Ports in interface global_signals_export
add_port_to_interface "global_signals_export" "ser_txd" "export"
add_port_to_interface "global_signals_export" "ser_rxd" "export"
add_port_to_interface "global_signals_export" "wd" "export"

# Interface avalon_master
add_interface "avalon_master" "avalon" "master" "avalon_master_clock"
set_interface_property "avalon_master" "burstOnBurstBoundariesOnly" "false"
set_interface_property "avalon_master" "doStreamReads" "false"
set_interface_property "avalon_master" "linewrapBursts" "false"
set_interface_property "avalon_master" "doStreamWrites" "false"
# Ports in interface avalon_master
add_port_to_interface "avalon_master" "address" "address"
add_port_to_interface "avalon_master" "writedata" "writedata"
add_port_to_interface "avalon_master" "byteenable" "byteenable"
add_port_to_interface "avalon_master" "readdata" "readdata"
add_port_to_interface "avalon_master" "read" "read"
add_port_to_interface "avalon_master" "write" "write"
add_port_to_interface "avalon_master" "waitrequest" "waitrequest"
