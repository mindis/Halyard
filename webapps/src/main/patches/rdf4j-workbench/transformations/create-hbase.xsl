<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE xsl:stylesheet>
<xsl:stylesheet version="1.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:sparql="http://www.w3.org/2005/sparql-results#" xmlns="http://www.w3.org/1999/xhtml">

	<xsl:include href="../locale/messages.xsl" />

	<xsl:variable name="title">
		<xsl:value-of select="$repository-create.title" />
	</xsl:variable>

	<xsl:include href="template.xsl" />

	<xsl:template match="sparql:sparql">
		<form action="create" method="post">
			<table class="dataentry">
				<tbody>
					<tr>
						<th>
							<xsl:value-of select="$repository-type.label" />
						</th>
						<td>
							<select id="type" name="type">
								<option value="hbase">
									Halyard HBase Store
								</option>
							</select>
						</td>
						<td></td>
					</tr>
					<tr>
						<th>
							<xsl:value-of select="$repository-id.label" />
						</th>
						<td>
							<input type="text" id="id" name="Repository ID" size="16"
								value="" />
						</td>
						<td></td>
					</tr>
					<tr>
						<th>
							<xsl:value-of select="$repository-title.label" />
						</th>
						<td>
							<input type="text" id="title" name="Repository title" size="48"
								value="" />
						</td>
						<td></td>
					</tr>
					<tr>
						<th>
							HBase Table Name
						</th>
						<td>
							<input type="text" id="tableName" name="HBase Table Name" size="48"
								value="" />
						</td>
						<td></td>
					</tr>
					<tr>
						<th>
							Create HBase Table if missing
						</th>
						<td>
							<input type="radio" name="Create HBase Table if missing" size="48"
								value="true" checked="true" />
							<xsl:value-of select="$true.label" />
							<input type="radio" name="Create HBase Table if missing" size="48"
								value="false" />
							<xsl:value-of select="$false.label" />
						</td>
						<td></td>
					</tr>
					<tr>
						<th>
							HBase Table presplit bits
						</th>
						<td>
							<input type="text" id="presplits" name="HBase Table presplit bits" size="4"
								value="0" />
						</td>
						<td></td>
					</tr>
					<tr>
						<th>
							Use Halyard Push Evaluation Strategy
						</th>
						<td>
							<input type="radio" name="Use Halyard Push Evaluation Strategy" size="48"
								value="true" checked="true" />
							<xsl:value-of select="$true.label" />
							<input type="radio" name="Use Halyard Push Evaluation Strategy" size="48"
								value="false" />
							<xsl:value-of select="$false.label" />
						</td>
						<td></td>
					</tr>
					<tr>
						<th>
							Query Evaluation Timeout (s)
						</th>
						<td>
							<input type="text" id="timeout" name="Query Evaluation Timeout" size="8"
								value="180" />
						</td>
						<td></td>
					</tr>
                                        <tr>
						<td></td>
						<td>
							<input type="button" value="{$cancel.label}" style="float:right"
								data-href="repositories"
                                onclick="document.location.href=this.getAttribute('data-href')" />
							<input id="create" type="button" value="{$create.label}"
								onclick="checkOverwrite()" />
						</td>
					</tr>
				</tbody>
			</table>
		</form>
		<script src="../../scripts/create.js" type="text/javascript">
		</script>
	</xsl:template>

</xsl:stylesheet>
