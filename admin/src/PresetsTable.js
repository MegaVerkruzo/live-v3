import React from "react";
import { PresetsTableRow } from "./PresetsTableRow";
import { Table, TableBody } from "@mui/material";

export class PresetsTable extends React.Component {
    constructor(props) {
        super(props);
    }

    render() {
        return (
            <Table align={ "center" } aria-label="simple table" sx={{ maxWidth: "md" }}>
                <TableBody>
                    {this.props.items !== undefined &&
                    this.props.items.map((row) =>
                        <PresetsTableRow
                            activeColor={ this.props.activeColor }
                            inactiveColor={ this.props.inactiveColor }
                            path={ this.props.path }
                            row={row}
                            key={row.id}
                            keys={this.props.keys}/>)
                    }
                </TableBody>
            </Table>
        );
    }
}
